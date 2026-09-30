package org.kore.raumschiffwerft.service.control.journal;

import java.util.Objects;

/**
 * Ein ungesendeter Journaleintrag der Outbox: Outbox-Id, Auftrags-ID
 * (als String, dient als Kafka-Key und Dokument-ID) und der Payload
 * (jsonb als String). Die Kopplung zur Komponente kaufauftrag besteht
 * ausschliesslich ueber das Tabellenformat.
 */
public record Journaleintrag(long id, String auftragsId, String payload) {

    public Journaleintrag {
        Objects.requireNonNull(auftragsId);
        Objects.requireNonNull(payload);
    }
}
