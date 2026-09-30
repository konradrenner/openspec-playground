package org.kore.raumschiffwerft.adapter.imperium.control;

import jakarta.enterprise.context.ApplicationScoped;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.AbfrageBestellstatus;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.AbfrageBestellstatusResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoerer;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoererResponse;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;

/**
 * Uebersetzt zwischen dem kanonischen Modell und den SOAP-Typen der
 * Imperiumswerft (Codegenerat aus der WSDL).
 */
@ApplicationScoped
public class ImperiumUebersetzer {

    public BestelleSternenzerstoerer bestellung(AuftragsId auftragsId, Kaufauftrag auftrag) {
        BestelleSternenzerstoerer bestellung = new BestelleSternenzerstoerer();
        bestellung.setAuftragsReferenz(auftragsId.wert().toString());
        bestellung.setBesteller(auftrag.kaeufer());
        bestellung.setKlasse(klassenCode(auftrag.klasse()));
        bestellung.setStueckzahl(auftrag.anzahl());
        bestellung.setZielwelt(auftrag.lieferplanet());
        return bestellung;
    }

    public AbfrageBestellstatus abfrage(AuftragsId auftragsId) {
        AbfrageBestellstatus abfrage = new AbfrageBestellstatus();
        abfrage.setAuftragsReferenz(auftragsId.wert().toString());
        return abfrage;
    }

    public Zustellbestaetigung bestaetigung(BestelleSternenzerstoererResponse antwort) {
        return new Zustellbestaetigung(antwort.getBestellnummer());
    }

    public Verarbeitungsstatus status(AbfrageBestellstatusResponse antwort) {
        Verarbeitungsstatus.Status status = switch (antwort.getStatus()) {
            case "IN_BEARBEITUNG" -> Verarbeitungsstatus.Status.IN_BEARBEITUNG;
            case "ABGESCHLOSSEN" -> Verarbeitungsstatus.Status.ABGESCHLOSSEN;
            default -> Verarbeitungsstatus.Status.UNBEKANNT;
        };
        String bestellnummer = antwort.getBestellnummer();
        if (bestellnummer == null || bestellnummer.isBlank()) {
            return Verarbeitungsstatus.von(status);
        }
        return Verarbeitungsstatus.mitExternerReferenz(status, bestellnummer);
    }

    private String klassenCode(Sternenzerstoererklasse klasse) {
        return switch (klasse) {
            case IMPERIAL_I -> "ISD_I";
            case IMPERIAL_II -> "ISD_II";
            case VICTORY -> "VICTORY";
            case EXECUTOR -> "EXECUTOR";
        };
    }
}
