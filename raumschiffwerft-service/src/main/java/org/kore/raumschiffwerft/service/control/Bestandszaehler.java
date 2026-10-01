package org.kore.raumschiffwerft.service.control;

/**
 * Port des control auf die Bestandszaehlungen der Betriebsmetriken.
 * Die Implementierung (JDBC) liegt in boundary/persistence; das control
 * kennt kein SQL. Zaehlt wird bei Abfrage (kein Poll-Thread).
 */
public interface Bestandszaehler {

    /** Anzahl offener Zustellungen (IN_ZUSTELLUNG, IN_ABGLEICH, UNGEKLAERT). */
    long offeneZustellungen();

    /** Anzahl endgueltig fehlgeschlagener Zustellungen (FEHLGESCHLAGEN). */
    long fehlgeschlageneZustellungen();

    /** Anzahl ungesendeter journal_outbox-Zeilen (Relay-Rueckstand). */
    long outboxRueckstand();
}
