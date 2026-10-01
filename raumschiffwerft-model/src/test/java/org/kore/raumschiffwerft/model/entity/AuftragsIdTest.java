package org.kore.raumschiffwerft.model.entity;

import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuftragsIdTest {

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
    void gleicheUuidIstGleich() {
        UUID uuid = UUID.randomUUID();
        AuftragsId erste = new AuftragsId(uuid);
        AuftragsId zweite = new AuftragsId(uuid);

        assertEquals(erste, zweite);
        assertEquals(erste.hashCode(), zweite.hashCode());
    }

    @Test
    void verschiedeneUuidsSindUngleich() {
        AuftragsId erste = new AuftragsId(UUID.randomUUID());
        AuftragsId zweite = new AuftragsId(UUID.randomUUID());

        assertNotEquals(erste, zweite);
    }

    @Test
    void konstruktionOhnePruefung() {
        assertDoesNotThrow(() -> new AuftragsId(null));
    }

    @Test
    void nullWirdAbgelehnt() {
        assertTrue(!validator.validate(new AuftragsId(null)).isEmpty());
        assertTrue(validator.validate(new AuftragsId(UUID.randomUUID())).isEmpty());
    }
}
