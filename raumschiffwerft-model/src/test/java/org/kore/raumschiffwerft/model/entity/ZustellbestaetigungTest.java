package org.kore.raumschiffwerft.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZustellbestaetigungTest {

    @Test
    void bestaetigungTraegtExterneReferenz() {
        assertEquals("ISD-4711", new Zustellbestaetigung("ISD-4711").externeReferenz());
    }

    @Test
    void leereReferenzWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> new Zustellbestaetigung(null));
        assertThrows(IllegalArgumentException.class, () -> new Zustellbestaetigung(" "));
    }
}
