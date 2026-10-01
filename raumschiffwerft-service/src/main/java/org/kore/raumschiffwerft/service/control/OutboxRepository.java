package org.kore.raumschiffwerft.service.control;

import java.time.OffsetDateTime;

import org.kore.raumschiffwerft.model.entity.AuftragsId;

/**
 * Port des control auf die Journal-Outbox. Die Implementierung (JDBC)
 * liegt in boundary/persistence; das control kennt kein SQL.
 */
public interface OutboxRepository {

    /**
     * Fuegt den Journaleintrag im Rahmen der Annahme-Transaktion ein;
     * gesendet_am bleibt ungesetzt (das Relay sendet spaeter).
     */
    void journalEinfuegen(AuftragsId auftragsId, String payloadJson, OffsetDateTime erstelltAm);
}
