package org.kore.raumschiffwerft.model.entity;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Identitaet eines Auftrags der Raumschiffwerft, gekapselt als UUID.
 * Die Pflichtregel ist als Bean-Validation-Constraint deklariert.
 */
public record AuftragsId(@NotNull UUID wert) {
}
