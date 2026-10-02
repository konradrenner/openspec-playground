package org.kore.raumschiffwerft.adapter.imperium.boundary;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.kore.raumschiffwerft.adapter.imperium.control.ImperiumUebersetzer;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.AbfrageBestellstatusResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoererResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.ImperiumWerft;
import org.kore.raumschiffwerft.model.entity.Zielsystem;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;

/**
 * Zielsystem-Implementierung fuer das Imperium: setzt kanonische Auftraege
 * per SOAP an der Imperiumswerft ab. Jeder technische Misserfolg (SOAP-Fault,
 * HTTP-Fehler, Timeout) wird als {@link ZustellungUngeklaert} gemeldet -
 * ohne Retries, ohne Datenbankzugriff. Jeder externe Aufruf laeuft in
 * einem eigenen Client-Span (adapter.imperium.*) mit Zielsystem und
 * Auftrags-ID als Attribut; Fehler markieren den Span.
 */
@ApplicationScoped
public class ImperiumZielsystem implements Zielsystem {

    private static final String ATTRIBUTE_ZIELSYSTEM = "durchlauferhitzer.zielsystem";
    private static final String ATTRIBUTE_AUFTRAG = "durchlauferhitzer.auftrag.id";

    private final ImperiumWerft werft;
    private final ImperiumUebersetzer uebersetzer;
    private final Tracer tracer;

    public ImperiumZielsystem(@CXFClient("imperium") ImperiumWerft werft, ImperiumUebersetzer uebersetzer,
                              Instance<OpenTelemetry> openTelemetry) {
        this.werft = werft;
        this.uebersetzer = uebersetzer;
        this.tracer = (openTelemetry.isUnsatisfied() ? OpenTelemetry.noop() : openTelemetry.get())
                .getTracer("durchlauferhitzer.adapter.imperium");
    }

    @Override
    public Zielsystemtyp typ() {
        return Zielsystemtyp.IMPERIUM;
    }

    @Override
    public Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag auftrag) throws ZustellungUngeklaert {
        Span span = spanStart("adapter.imperium.zustellen", auftragsId);
        try (Scope ignoriert = span.makeCurrent()) {
            BestelleSternenzerstoererResponse antwort =
                    werft.bestelleSternenzerstoerer(uebersetzer.bestellung(auftragsId, auftrag));
            return uebersetzer.bestaetigung(antwort);
        } catch (RuntimeException e) {
            fehlerMarkieren(span, e);
            throw new ZustellungUngeklaert("Bestellung beim Imperium gescheitert (auftragsReferenz=%s)"
                    .formatted(auftragsId.wert()), e);
        } finally {
            span.end();
        }
    }

    @Override
    public Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId) throws ZustellungUngeklaert {
        Span span = spanStart("adapter.imperium.statusAbfragen", auftragsId);
        try (Scope ignoriert = span.makeCurrent()) {
            AbfrageBestellstatusResponse antwort = werft.abfrageBestellstatus(uebersetzer.abfrage(auftragsId));
            return uebersetzer.status(antwort);
        } catch (RuntimeException e) {
            fehlerMarkieren(span, e);
            throw new ZustellungUngeklaert("Statusabfrage beim Imperium gescheitert (auftragsReferenz=%s)"
                    .formatted(auftragsId.wert()), e);
        } finally {
            span.end();
        }
    }

    private Span spanStart(String name, AuftragsId auftragsId) {
        return tracer.spanBuilder(name)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(ATTRIBUTE_ZIELSYSTEM, Zielsystemtyp.IMPERIUM.name())
                .setAttribute(ATTRIBUTE_AUFTRAG, auftragsId.wert().toString())
                .startSpan();
    }

    private static void fehlerMarkieren(Span span, RuntimeException fehler) {
        span.recordException(fehler);
        span.setStatus(StatusCode.ERROR);
    }
}
