package org.kore.raumschiffwerft.adapter.rebellion.control;

import jakarta.enterprise.context.ApplicationScoped;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAnfrage;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;

/**
 * Uebersetzt zwischen dem kanonischen Modell und den Feldern der
 * Rebellen-Beschaffungs-API.
 */
@ApplicationScoped
public class RebellionUebersetzer {

    public BeschaffungsAnfrage anfrage(AuftragsId auftragsId, Kaufauftrag auftrag) {
        return new BeschaffungsAnfrage(auftragsId.wert().toString(), auftrag.kaeufer(), schiffstyp(auftrag.klasse()),
                auftrag.anzahl(), auftrag.lieferplanet());
    }

    private String schiffstyp(Sternenzerstoererklasse klasse) {
        return switch (klasse) {
            case IMPERIAL_I -> "imperial-1";
            case IMPERIAL_II -> "imperial-2";
            case VICTORY -> "victory";
            case EXECUTOR -> "executor";
        };
    }
}
