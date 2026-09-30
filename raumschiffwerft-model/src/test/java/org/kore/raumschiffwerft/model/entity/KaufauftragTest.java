package org.kore.raumschiffwerft.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KaufauftragTest {

    private Kaufauftrag auftrag(String kaeufer, int anzahl, int lieferplanet) {
        return new Kaufauftrag(kaeufer, Sternenzerstoererklasse.VICTORY, anzahl, lieferplanet);
    }

    @Test
    void gueltigerAuftragWirdAngenommen() {
        Kaufauftrag auftrag = new Kaufauftrag("Mon Mothma", Sternenzerstoererklasse.VICTORY, 3, 42);

        assertEquals("Mon Mothma", auftrag.kaeufer());
        assertEquals(Sternenzerstoererklasse.VICTORY, auftrag.klasse());
        assertEquals(3, auftrag.anzahl());
        assertEquals(42, auftrag.lieferplanet());
    }

    @Test
    void jederKlassenwertIstErlaubt() {
        for (Sternenzerstoererklasse klasse : Sternenzerstoererklasse.values()) {
            assertEquals(klasse, new Kaufauftrag("Kaempfer", klasse, 1, 1).klasse());
        }
    }

    @Test
    void kaeuferAusserhalbDesWertebereichsWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> auftrag(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> auftrag("", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> auftrag("   ", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> auftrag("x".repeat(101), 1, 1));
        assertDoesNotThrow(() -> auftrag("x".repeat(100), 1, 1));
    }

    @Test
    void klasseNullWirdAbgelehnt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Kaufauftrag("Mon Mothma", null, 1, 1));
        assertEquals("klasse darf nicht null sein", ex.getMessage());
    }

    @Test
    void anzahlAusserhalbDesWertebereichsWirdAbgelehnt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> auftrag("Kaempfer", 0, 1));
        assertEquals("anzahl muss zwischen 1 und 12 liegen, war: 0", ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> auftrag("Kaempfer", 13, 1));
        assertDoesNotThrow(() -> auftrag("Kaempfer", 12, 1));
    }

    @Test
    void lieferplanetAusserhalbDesWertebereichsWirdAbgelehnt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> auftrag("Kaempfer", 1, 0));
        assertEquals("lieferplanet muss zwischen 1 und 60 liegen, war: 0", ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> auftrag("Kaempfer", 1, 61));
        assertDoesNotThrow(() -> auftrag("Kaempfer", 1, 60));
    }
}
