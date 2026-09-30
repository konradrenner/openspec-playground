package org.kore.raumschiffwerft.adapter.rebellion.boundary;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kore.raumschiffwerft.adapter.rebellion.control.RebellionUebersetzer;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAntwort;
import org.kore.raumschiffwerft.adapter.rebellion.entity.StatusAntwort;
import org.kore.raumschiffwerft.model.boundary.Zielsystem;
import org.kore.raumschiffwerft.model.boundary.ZustellungUngeklaert;
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
 * Verarbeitungsstatus UNBEKANNT interpretiert.
 */
@ApplicationScoped
public class RebellionZielsystem implements Zielsystem {

    private static final int STATUS_NICHT_GEFUNDEN = 404;

    private final RebellionBeschaffungClient client;
    private final RebellionUebersetzer uebersetzer;

    @Inject
    public RebellionZielsystem(@RestClient RebellionBeschaffungClient client, RebellionUebersetzer uebersetzer) {
        this.client = client;
        this.uebersetzer = uebersetzer;
    }

    @Override
    public Zielsystemtyp typ() {
        return Zielsystemtyp.REBELLION;
    }

    @Override
    public Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag auftrag) throws ZustellungUngeklaert {
        try {
            BeschaffungsAntwort antwort = client.beschaffungAnlegen(uebersetzer.anfrage(auftragsId, auftrag));
            return new Zustellbestaetigung(antwort.beschaffungsId());
        } catch (RuntimeException e) {
            throw new ZustellungUngeklaert("Beschaffung bei der Rebellion gescheitert (referenz=%s)"
                    .formatted(auftragsId.wert()), e);
        }
    }

    @Override
    public Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId) throws ZustellungUngeklaert {
        try {
            return status(client.statusAbfragen(auftragsId.wert().toString()));
        } catch (RuntimeException e) {
            if (istNichtGefunden(e)) {
                return Verarbeitungsstatus.von(Verarbeitungsstatus.Status.UNBEKANNT);
            }
            throw new ZustellungUngeklaert("Statusabfrage bei der Rebellion gescheitert (referenz=%s)"
                    .formatted(auftragsId.wert()), e);
        }
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
