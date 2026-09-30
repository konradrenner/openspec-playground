package org.kore.raumschiffwerft.model.entity;

/**
 * Kanonischer Verarbeitungsstand eines Auftrags im Zielsystem,
 * gegebenenfalls mit der vom Zielsystem vergebenen externen Referenz.
 */
public record Verarbeitungsstatus(Status status, String externeReferenz) {

    public enum Status {
        ABGESCHLOSSEN,
        IN_BEARBEITUNG,
        UNBEKANNT
    }

    public Verarbeitungsstatus {
        if (status == null) {
            throw new IllegalArgumentException("status darf nicht null sein");
        }
    }

    public static Verarbeitungsstatus von(Status status) {
        return new Verarbeitungsstatus(status, null);
    }

    public static Verarbeitungsstatus mitExternerReferenz(Status status, String externeReferenz) {
        if (externeReferenz == null || externeReferenz.isBlank()) {
            throw new IllegalArgumentException("externeReferenz darf nicht leer sein");
        }
        return new Verarbeitungsstatus(status, externeReferenz);
    }
}
