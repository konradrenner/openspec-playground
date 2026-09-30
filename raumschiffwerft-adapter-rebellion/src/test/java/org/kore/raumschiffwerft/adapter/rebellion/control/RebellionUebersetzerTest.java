package org.kore.raumschiffwerft.adapter.rebellion.control;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAnfrage;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RebellionUebersetzerTest {

    private final RebellionUebersetzer uebersetzer = new RebellionUebersetzer();

    @Test
    void anfrageUebersetztAlleFelder() {
        AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
        Kaufauftrag auftrag = new Kaufauftrag("Leia Organa", Sternenzerstoererklasse.IMPERIAL_II, 4, 12);

        BeschaffungsAnfrage anfrage = uebersetzer.anfrage(auftragsId, auftrag);

        assertEquals(auftragsId.wert().toString(), anfrage.referenz());
        assertEquals("Leia Organa", anfrage.auftraggeber());
        assertEquals("imperial-2", anfrage.schiffstyp());
        assertEquals(4, anfrage.menge());
        assertEquals(12, anfrage.zielplanet());
    }

    @Test
    void klassenWerdenAufSchiffstypenAbgebildet() {
        assertEquals("imperial-1", schiffstyp(Sternenzerstoererklasse.IMPERIAL_I));
        assertEquals("imperial-2", schiffstyp(Sternenzerstoererklasse.IMPERIAL_II));
        assertEquals("victory", schiffstyp(Sternenzerstoererklasse.VICTORY));
        assertEquals("executor", schiffstyp(Sternenzerstoererklasse.EXECUTOR));
    }

    private String schiffstyp(Sternenzerstoererklasse klasse) {
        return uebersetzer.anfrage(new AuftragsId(UUID.randomUUID()),
                new Kaufauftrag("Kaempfer", klasse, 1, 1)).schiffstyp();
    }
}
