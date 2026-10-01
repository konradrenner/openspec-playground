package org.kore.raumschiffwerft.model.entity;

import jakarta.validation.constraints.NotBlank;

/**
 * Bestaetigung eines Zielsystems, dass es einen Auftrag angenommen hat;
 * traegt die vom Zielsystem vergebene externe Referenz.
 */
public record Zustellbestaetigung(@NotBlank String externeReferenz) {
}
