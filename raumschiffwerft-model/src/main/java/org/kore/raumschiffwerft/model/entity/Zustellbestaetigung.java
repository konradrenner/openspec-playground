package org.kore.raumschiffwerft.model.entity;

/**
 * Bestaetigung eines Zielsystems, dass es einen Auftrag angenommen hat;
 * traegt die vom Zielsystem vergebene externe Referenz.
 */
public record Zustellbestaetigung(String externeReferenz) {

    public Zustellbestaetigung {
        if (externeReferenz == null || externeReferenz.isBlank()) {
            throw new IllegalArgumentException("externeReferenz darf nicht leer sein");
        }
    }
}
