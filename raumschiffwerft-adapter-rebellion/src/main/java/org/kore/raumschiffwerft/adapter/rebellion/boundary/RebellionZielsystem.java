package org.kore.raumschiffwerft.adapter.rebellion.boundary;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kore.raumschiffwerft.adapter.rebellion.control.RebellionUebersetzer;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAntwort;
import org.kore.raumschiffwerft.adapter.rebellion.entity.StatusAntwort;
import org.kore.raumschiffwerft.model.entity.Zielsystem;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;

/**
 * Zielsystem-Implementierung fuer die Rebellion: setzt kanonische Auftraege
 * per REST an der Beschaffungs-API ab. Jeder technische Misserfolg
 * (Nicht-2xx, Timeout) wird als {@link ZustellungUngeklaert} gemeldet -
 * ohne Retries, ohne Datenbankzugriff. Eine 404-Antwort der Statusabfrage
 * bedeutet laut API-Vertrag "unbekannter Auftrag" und wird als
 * Verarbeitungsstatus UNBEKANNT interpretiert. Jeder externe Aufruf laeuft
 * in einem eigenen Client-Span (adapter.rebellion.*) mit Zielsystem und
 * Auftrags-ID als Attribut; Fehler markieren den Span.
 */
@ApplicationScoped
public class RebellionZielsystem implements Zielsystem {

    private static final int STATUS_NICHT_GEFUNDEN = 404;

    private static final String ATTRIBUTE_ZIELSYSTEM = "durchlauferhitzer.zielsystem";
    private static final String ATTRIBUTE_AUFTRAG = "durchlauferhitzer.auftrag.id";

    private final RebellionBeschaffungClient client;
    private final RebellionUebersetzer uebersetzer;
    private final Tracer tracer;

    @Inject
    public RebellionZielsystem(@RestClient RebellionBeschaffungClient client, RebellionUebersetzer uebersetzer,
                               Instance<OpenTelemetry> openTelemetry) {
        this.client = client;
        this.uebersetzer = uebersetzer;
        this.tracer = (openTelemetry.isUnsatisfied() ? OpenTelemetry.noop() : openTelemetry.get())
                .getTracer("durchlauferhitzer.adapter.rebellion");
    }

    @Override
    public Zielsystemtyp typ() {
        return Zielsystemtyp.REBELLION;
    }

    @Override
    public Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag auftrag) throws ZustellungUngeklaert {
        Span span = spanStart("adapter.rebellion.zustellen", auftragsId);
        try (Scope ignoriert = span.makeCurrent()) {
            BeschaffungsAntwort antwort = client.beschaffungAnlegen(uebersetzer.anfrage(auftragsId, auftrag));
            return new Zustellbestaetigung(antwort.beschaffungsId());
        } catch (RuntimeException e) {
            fehlerMarkieren(span, e);
            throw new ZustellungUngeklaert("Beschaffung bei der Rebellion gescheitert (referenz=%s)"
                    .formatted(auftragsId.wert()), e);
        } finally {
            span.end();
        }
    }

    @Override
    public Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId) throws ZustellungUngeklaert {
        Span span = spanStart("adapter.rebellion.statusAbfragen", auftragsId);
        try (Scope ignoriert = span.makeCurrent()) {
            return status(client.statusAbfragen(auftragsId.wert().toString()));
        } catch (RuntimeException e) {
            if (istNichtGefunden(e)) {
                return Verarbeitungsstatus.von(Verarbeitungsstatus.Status.UNBEKANNT);
            }
            fehlerMarkieren(span, e);
            throw new ZustellungUngeklaert("Statusabfrage bei der Rebellion gescheitert (referenz=%s)"
                    .formatted(auftragsId.wert()), e);
        } finally {
            span.end();
        }
    }

    private Span spanStart(String name, AuftragsId auftragsId) {
        return tracer.spanBuilder(name)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(ATTRIBUTE_ZIELSYSTEM, Zielsystemtyp.REBELLION.name())
                .setAttribute(ATTRIBUTE_AUFTRAG, auftragsId.wert().toString())
                .startSpan();
    }

    private static void fehlerMarkieren(Span span, RuntimeException fehler) {
        span.recordException(fehler);
        span.setStatus(StatusCode.ERROR);
    }

    /**
     * Erkennt eine 404-Antwort, auch wenn der REST-Client sie in eine
     * frameworkspezifische Exception eingepackt hat.
     */
    private boolean istNichtGefunden(Throwable fehler) {
        for (Throwable ursache = fehler; ursache != null; ursache = ursache.getCause()) {
            if (ursache instanceof WebApplicationException wae
                    && wae.getResponse() != null
                    && wae.getResponse().getStatus() == STATUS_NICHT_GEFUNDEN) {
                return true;
            }
            if (ursache.getCause() == ursache) {
                break;
            }
        }
        return false;
    }

    private Verarbeitungsstatus status(StatusAntwort antwort) {
        Verarbeitungsstatus.Status status = switch (antwort.status()) {
            case "IN_ARBEIT" -> Verarbeitungsstatus.Status.IN_BEARBEITUNG;
            case "ERLEDIGT" -> Verarbeitungsstatus.Status.ABGESCHLOSSEN;
            default -> Verarbeitungsstatus.Status.UNBEKANNT;
        };
        return Verarbeitungsstatus.von(status);
    }
}
