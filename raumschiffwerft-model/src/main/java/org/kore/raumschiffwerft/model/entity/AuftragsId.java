package org.kore.raumschiffwerft.model.entity;

import java.util.UUID;

/**
 * Identitaet eines Auftrags der Raumschiffwerft, gekapselt als UUID.
 */
public record AuftragsId(UUID wert) {

    public AuftragsId {
        if (wert == null) {
            throw new IllegalArgumentException("wert darf nicht null sein");
        }
    }
}
