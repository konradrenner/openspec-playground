package org.kore.raumschiffwerft.model.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KaufauftragTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void validatorAufbauen() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void validatorSchliessen() {
        factory.close();
    }

    private Kaufauftrag auftrag(String kaeufer, Sternenzerstoererklasse klasse, int anzahl, int lieferplanet) {
        return new Kaufauftrag(kaeufer, klasse, anzahl, lieferplanet);
    }

    @Test
    void gueltigerAuftragWirdAngenommen() {
        Kaufauftrag auftrag = new Kaufauftrag("Mon Mothma", Sternenzerstoererklasse.VICTORY, 3, 42);

        assertEquals("Mon Mothma", auftrag.kaeufer());
        assertEquals(Sternenzerstoererklasse.VICTORY, auftrag.klasse());
        assertEquals(3, auftrag.anzahl());
        assertEquals(42, auftrag.lieferplanet());
        assertTrue(validator.validate(auftrag).isEmpty());
    }

    @Test
    void jederKlassenwertIstErlaubt() {
        for (Sternenzerstoererklasse klasse : Sternenzerstoererklasse.values()) {
            Kaufauftrag auftrag = new Kaufauftrag("Kaempfer", klasse, 1, 1);
            assertEquals(klasse, auftrag.klasse());
            assertTrue(validator.validate(auftrag).isEmpty());
        }
    }

    @Test
    void konstruktionOhnePruefung() {
        assertDoesNotThrow(() -> new Kaufauftrag(null, null, 0, 0));
    }

    @Test
    void kaeuferAusserhalbDesWertebereichsWirdAbgelehnt() {
        assertTrue(!validator.validate(auftrag(null, Sternenzerstoererklasse.VICTORY, 1, 1)).isEmpty());
        assertTrue(!validator.validate(auftrag("", Sternenzerstoererklasse.VICTORY, 1, 1)).isEmpty());
        assertTrue(!validator.validate(auftrag("   ", Sternenzerstoererklasse.VICTORY, 1, 1)).isEmpty());
        assertTrue(!validator.validate(auftrag("x".repeat(101), Sternenzerstoererklasse.VICTORY, 1, 1)).isEmpty());
        assertTrue(validator.validate(auftrag("x".repeat(100), Sternenzerstoererklasse.VICTORY, 1, 1)).isEmpty());
    }

    @Test
    void klasseNullWirdAbgelehnt() {
        assertTrue(!validator.validate(auftrag("Mon Mothma", null, 1, 1)).isEmpty());
    }

    @Test
    void anzahlAusserhalbDesWertebereichsWirdAbgelehnt() {
        assertTrue(!validator.validate(auftrag("Kaempfer", Sternenzerstoererklasse.VICTORY, 0, 1)).isEmpty());
        assertTrue(!validator.validate(auftrag("Kaempfer", Sternenzerstoererklasse.VICTORY, 13, 1)).isEmpty());
        assertTrue(validator.validate(auftrag("Kaempfer", Sternenzerstoererklasse.VICTORY, 12, 1)).isEmpty());
    }

    @Test
    void lieferplanetAusserhalbDesWertebereichsWirdAbgelehnt() {
        assertTrue(!validator.validate(auftrag("Kaempfer", Sternenzerstoererklasse.VICTORY, 1, 0)).isEmpty());
        assertTrue(!validator.validate(auftrag("Kaempfer", Sternenzerstoererklasse.VICTORY, 1, 61)).isEmpty());
        assertTrue(validator.validate(auftrag("Kaempfer", Sternenzerstoererklasse.VICTORY, 1, 60)).isEmpty());
    }
}
