package org.kore.raumschiffwerft.service.control;

/**
 * Die Datenbank ist bei der Annahme nicht erreichbar; es wurde nichts
 * geschrieben. Wird als 503 mit Retry-After beantwortet.
 */
public class DatenbankNichtErreichbar extends RuntimeException {

    public DatenbankNichtErreichbar(Throwable ursache) {
        super("Datenbank nicht erreichbar", ursache);
    }
}
