package org.kore.raumschiffwerft.service.control.journal;

/**
 * Port der Journal-Komponente auf die Transaktionsfuehrung der Datenbank.
 * Die Implementierung (Agroal, JDBC) liegt in boundary/persistence/journal;
 * der Relay kennt weder Verbindungen noch SQL. Ein Durchlauf laeuft in
 * EINER kurzen Transaktion: Zeilen beanspruchen, senden, markieren,
 * committen - ein Sendefehler rollt alles zurueck (at-least-once).
 */
public interface JournalTransaktionen {

    /**
     * Fuehrt die Arbeit in einer Transaktion aus: bei Rueckkehr wurde
     * committet, bei jedem Fehler wurde zurueckgerollt und der Fehler als
     * IllegalStateException gemeldet.
     */
    <T> T inTransaktion(Arbeit<T> arbeit);

    /** Arbeitseinheit innerhalb einer Transaktion. */
    @FunctionalInterface
    interface Arbeit<T> {

        T ausfuehren() throws Exception;
    }
}
