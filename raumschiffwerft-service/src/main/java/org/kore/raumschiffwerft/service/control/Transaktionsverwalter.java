package org.kore.raumschiffwerft.service.control;

/**
 * Port des control auf die Transaktionsfuehrung der Datenbank. Die
 * Implementierung (Agroal, JDBC) liegt in boundary/persistence; das
 * control kennt weder Verbindungen noch SQL. Jede Arbeit laeuft in
 * EINER Transaktion: Commit bei Erfolg, Rollback bei jedem Fehler.
 */
public interface Transaktionsverwalter {

    /**
     * Fuehrt die Arbeit in einer Transaktion aus: bei Rueckkehr wurde
     * committet, bei jedem Fehler der Arbeit (auch geprueftem) wurde
     * zurueckgerollt und der Fehler als IllegalStateException gemeldet.
     * Ist die Datenbank nicht erreichbar, wird DatenbankNichtErreichbar
     * gemeldet.
     */
    <T> T inTransaktion(Arbeit<T> arbeit);

    /** Arbeitseinheit innerhalb einer Transaktion. */
    @FunctionalInterface
    interface Arbeit<T> {

        T ausfuehren() throws Exception;
    }
}
