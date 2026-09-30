package org.kore.raumschiffwerft.adapter.imperium.boundary;

import io.quarkiverse.cxf.annotation.CXFClient;
import jakarta.enterprise.context.ApplicationScoped;
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
 * ohne Retries, ohne Datenbankzugriff.
 */
@ApplicationScoped
public class ImperiumZielsystem implements Zielsystem {

    private final ImperiumWerft werft;
    private final ImperiumUebersetzer uebersetzer;

    public ImperiumZielsystem(@CXFClient("imperium") ImperiumWerft werft, ImperiumUebersetzer uebersetzer) {
        this.werft = werft;
        this.uebersetzer = uebersetzer;
    }

    @Override
    public Zielsystemtyp typ() {
        return Zielsystemtyp.IMPERIUM;
    }

    @Override
    public Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag auftrag) throws ZustellungUngeklaert {
        try {
            BestelleSternenzerstoererResponse antwort =
                    werft.bestelleSternenzerstoerer(uebersetzer.bestellung(auftragsId, auftrag));
            return uebersetzer.bestaetigung(antwort);
        } catch (RuntimeException e) {
            throw new ZustellungUngeklaert("Bestellung beim Imperium gescheitert (auftragsReferenz=%s)"
                    .formatted(auftragsId.wert()), e);
        }
    }

    @Override
    public Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId) throws ZustellungUngeklaert {
        try {
            AbfrageBestellstatusResponse antwort = werft.abfrageBestellstatus(uebersetzer.abfrage(auftragsId));
            return uebersetzer.status(antwort);
        } catch (RuntimeException e) {
            throw new ZustellungUngeklaert("Statusabfrage beim Imperium gescheitert (auftragsReferenz=%s)"
                    .formatted(auftragsId.wert()), e);
        }
    }
}
