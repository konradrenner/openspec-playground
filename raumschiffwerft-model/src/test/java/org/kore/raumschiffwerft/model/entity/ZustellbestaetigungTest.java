package org.kore.raumschiffwerft.model.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZustellbestaetigungTest {

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
    void bestaetigungTraegtExterneReferenz() {
        assertEquals("ISD-4711", new Zustellbestaetigung("ISD-4711").externeReferenz());
        assertTrue(validator.validate(new Zustellbestaetigung("ISD-4711")).isEmpty());
    }

    @Test
    void leereReferenzWirdAbgelehnt() {
        assertTrue(!validator.validate(new Zustellbestaetigung(null)).isEmpty());
        assertTrue(!validator.validate(new Zustellbestaetigung(" ")).isEmpty());
    }
}
