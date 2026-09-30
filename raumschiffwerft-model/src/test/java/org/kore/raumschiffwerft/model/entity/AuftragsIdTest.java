package org.kore.raumschiffwerft.model.entity;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuftragsIdTest {

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
    void nullWirdAbgelehnt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new AuftragsId(null));
        assertEquals("wert darf nicht null sein", ex.getMessage());
    }
}
