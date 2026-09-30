package org.kore.raumschiffwerft.service.control.journal;

import java.util.Objects;

/**
 * Ein ungesendeter Journaleintrag der Outbox: Outbox-Id, Auftrags-ID
 * (als String, dient als Kafka-Key und Dokument-ID), der Payload
 * (jsonb als String) und der darin gespeicherte traceparent (Kontext
 * des Annahme-Spans, dient dem Span-Link des Relays). Die Kopplung zur
 * Komponente kaufauftrag besteht ausschliesslich ueber das Tabellenformat.
 */
public record Journaleintrag(long id, String auftragsId, String payload, String traceparent) {

    public Journaleintrag {
        Objects.requireNonNull(auftragsId);
        Objects.requireNonNull(payload);
    }
}
