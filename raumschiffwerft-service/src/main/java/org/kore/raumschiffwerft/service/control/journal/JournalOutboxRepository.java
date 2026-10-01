package org.kore.raumschiffwerft.service.control.journal;

/**
 * Port des Journal-Relays auf die Outbox-Tabelle. Die Implementierung
 * (JDBC, boundary/persistence/journal) kapselt den kompletten Durchlauf in
 * EINER Transaktion: Zeilen mit FOR UPDATE SKIP LOCKED beanspruchen, je
 * Zeile den Versand aufrufen, danach als gesendet markieren, committen.
 * Ein Fehler des Versands rollt die Transaktion zurueck; der Relay kennt
 * weder Verbindungen noch SQL.
 */
public interface JournalOutboxRepository {

    /**
     * Sendet bis zu batch ungesendete Eintraege ueber den Versand und
     * markiert jeden Eintrag erst nach erfolgreichem Versand. Rueckgabe
     * ist die Anzahl der versendeten Eintraege.
     */
    int versenden(int batch, Versand versand);

    /** Versand eines Journaleintrags durch den Relay (Span, Kafka-Header). */
    @FunctionalInterface
    interface Versand {

        void senden(Journaleintrag eintrag) throws Exception;
    }
}
