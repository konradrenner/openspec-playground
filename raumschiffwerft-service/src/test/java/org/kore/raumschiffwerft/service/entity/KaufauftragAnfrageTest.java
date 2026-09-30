package org.kore.raumschiffwerft.service.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KaufauftragAnfrageTest {

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

    @Test
    void gueltigeAnfrageHatKeineVerstoesse() {
        assertTrue(validator.validate(new KaufauftragAnfrage("Mon Mothma", "VICTORY", 3, 42)).isEmpty());
    }

    @Test
    void kaeuferAusserhalbDesWertebereichsWirdAbgelehnt() {
        assertTrue(!validator.validate(new KaufauftragAnfrage(null, "VICTORY", 3, 42)).isEmpty());
        assertTrue(!validator.validate(new KaufauftragAnfrage("", "VICTORY", 3, 42)).isEmpty());
        assertTrue(!validator.validate(new KaufauftragAnfrage("   ", "VICTORY", 3, 42)).isEmpty());
        assertTrue(!validator.validate(new KaufauftragAnfrage("x".repeat(101), "VICTORY", 3, 42)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("x".repeat(100), "VICTORY", 3, 42)).isEmpty());
    }

    @Test
    void klasseNurAusDenVierWerten() {
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "IMPERIAL_I", 1, 1)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "IMPERIAL_II", 1, 1)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "VICTORY", 1, 1)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "EXECUTOR", 1, 1)).isEmpty());
        assertTrue(!validator.validate(new KaufauftragAnfrage("K", "ISD_I", 1, 1)).isEmpty());
        assertTrue(!validator.validate(new KaufauftragAnfrage("K", null, 1, 1)).isEmpty());
    }

    @Test
    void anzahlAusserhalbDesWertebereichsWirdAbgelehnt() {
        assertTrue(!validator.validate(new KaufauftragAnfrage("K", "VICTORY", 0, 42)).isEmpty());
        assertTrue(!validator.validate(new KaufauftragAnfrage("K", "VICTORY", 13, 42)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "VICTORY", 1, 42)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "VICTORY", 12, 42)).isEmpty());
    }

    @Test
    void lieferplanetAusserhalbDesWertebereichsWirdAbgelehnt() {
        assertTrue(!validator.validate(new KaufauftragAnfrage("K", "VICTORY", 3, 0)).isEmpty());
        assertTrue(!validator.validate(new KaufauftragAnfrage("K", "VICTORY", 3, 61)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "VICTORY", 3, 1)).isEmpty());
        assertTrue(validator.validate(new KaufauftragAnfrage("K", "VICTORY", 3, 60)).isEmpty());
    }
}
