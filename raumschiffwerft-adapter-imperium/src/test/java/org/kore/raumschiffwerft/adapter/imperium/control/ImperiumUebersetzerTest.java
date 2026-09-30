package org.kore.raumschiffwerft.adapter.imperium.control;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.AbfrageBestellstatusResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoerer;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoererResponse;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImperiumUebersetzerTest {

    private final ImperiumUebersetzer uebersetzer = new ImperiumUebersetzer();

    @Test
    void bestellungUebersetztAlleFelder() {
        AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
        Kaufauftrag auftrag = new Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_I, 5, 7);

        BestelleSternenzerstoerer bestellung = uebersetzer.bestellung(auftragsId, auftrag);

        assertEquals(auftragsId.wert().toString(), bestellung.getAuftragsReferenz());
        assertEquals("Tarkin", bestellung.getBesteller());
        assertEquals("ISD_I", bestellung.getKlasse());
        assertEquals(5, bestellung.getStueckzahl());
        assertEquals(7, bestellung.getZielwelt());
    }

    @Test
    void klassenWerdenAufSoapCodesAbgebildet() {
        assertEquals("ISD_I", klasse(Sternenzerstoererklasse.IMPERIAL_I));
        assertEquals("ISD_II", klasse(Sternenzerstoererklasse.IMPERIAL_II));
        assertEquals("VICTORY", klasse(Sternenzerstoererklasse.VICTORY));
        assertEquals("EXECUTOR", klasse(Sternenzerstoererklasse.EXECUTOR));
    }

    @Test
    void abfrageTraegtDieAuftragsReferenz() {
        AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());

        assertEquals(auftragsId.wert().toString(), uebersetzer.abfrage(auftragsId).getAuftragsReferenz());
    }

    @Test
    void bestaetigungTraegtDieBestellnummer() {
        BestelleSternenzerstoererResponse antwort = new BestelleSternenzerstoererResponse();
        antwort.setBestellnummer("ISD-4711");

        assertEquals("ISD-4711", uebersetzer.bestaetigung(antwort).externeReferenz());
    }

    @Test
    void statusWirdKanonischAbgebildet() {
        assertEquals(Verarbeitungsstatus.Status.IN_BEARBEITUNG,
                uebersetzer.status(status("IN_BEARBEITUNG", "ISD-4711")).status());
        assertEquals(Verarbeitungsstatus.Status.ABGESCHLOSSEN,
                uebersetzer.status(status("ABGESCHLOSSEN", "ISD-4711")).status());
        assertEquals(Verarbeitungsstatus.Status.UNBEKANNT,
                uebersetzer.status(status("UNBEKANNT", null)).status());
    }

    @Test
    void statusMitBestellnummerTraegtExterneReferenz() {
        Verarbeitungsstatus status = uebersetzer.status(status("IN_BEARBEITUNG", "ISD-4711"));

        assertEquals(Verarbeitungsstatus.Status.IN_BEARBEITUNG, status.status());
        assertEquals("ISD-4711", status.externeReferenz());
    }

    @Test
    void statusOhneBestellnummerHatKeineExterneReferenz() {
        Verarbeitungsstatus status = uebersetzer.status(status("ABGESCHLOSSEN", " "));

        assertEquals(Verarbeitungsstatus.Status.ABGESCHLOSSEN, status.status());
        assertEquals(null, status.externeReferenz());
    }

    private String klasse(Sternenzerstoererklasse klasse) {
        return uebersetzer.bestellung(new AuftragsId(UUID.randomUUID()),
                new Kaufauftrag("Kaempfer", klasse, 1, 1)).getKlasse();
    }

    private AbfrageBestellstatusResponse status(String status, String bestellnummer) {
        AbfrageBestellstatusResponse antwort = new AbfrageBestellstatusResponse();
        antwort.setStatus(status);
        antwort.setBestellnummer(bestellnummer);
        return antwort;
    }
}
