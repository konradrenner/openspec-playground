package org.kore.raumschiffwerft.service.control.journal;

/**
 * Port des Journal-Relays auf den Kafka-Versand. Die Implementierung
 * liegt im boundary-Package der Komponente journal (Camel-Route);
 * der Relay kennt weder Camel noch Kafka.
 */
public interface JournalVersand {

    /**
     * Sendet einen Journaleintrag synchron an das Topic und wartet auf
     * die Bestaetigung der Broker (acks=all). Key ist die Auftrags-ID.
     * Jeder Misserfolg wird als RuntimeException gemeldet.
     */
    void senden(String auftragsId, String payload);
}
