package org.kore.raumschiffwerft.service.control;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import jakarta.enterprise.context.ApplicationScoped;
import org.kore.raumschiffwerft.model.entity.AuftragsId;

/**
 * Schreibt die Journaleintraege (Outbox) per plain JDBC. In diesem Change
 * wird nur geschrieben; gesendet_am bleibt ungesetzt (kein Kafka).
 */
@ApplicationScoped
public class OutboxRepository {

    private static final String INSERT =
            "INSERT INTO journal_outbox (auftrags_id, payload, erstellt_am) VALUES (?, ?::jsonb, ?)";

    private final io.agroal.api.AgroalDataSource dataSource;

    public OutboxRepository(io.agroal.api.AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Fuegt den Journaleintrag im Rahmen der Annahme-Transaktion ein. */
    public void journalEinfuegen(Connection verbindung, AuftragsId auftragsId, String payloadJson,
                                 OffsetDateTime erstelltAm) throws SQLException {
        try (var ps = verbindung.prepareStatement(INSERT)) {
            ps.setObject(1, auftragsId.wert());
            ps.setString(2, payloadJson);
            ps.setObject(3, erstelltAm);
            ps.executeUpdate();
        }
    }
}
