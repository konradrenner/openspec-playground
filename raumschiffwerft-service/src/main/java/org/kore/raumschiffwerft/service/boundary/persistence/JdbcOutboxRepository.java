package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.service.control.OutboxRepository;

/**
 * Schreibt die Journaleintraege (Outbox) per plain JDBC. In diesem Change
 * wird nur geschrieben; gesendet_am bleibt ungesetzt (kein Kafka).
 */
@ApplicationScoped
public class JdbcOutboxRepository implements OutboxRepository {

    private static final String INSERT =
            "INSERT INTO journal_outbox (auftrags_id, payload, erstellt_am) VALUES (?, ?::jsonb, ?)";

    private final Verbindungen verbindungen;

    @Inject
    public JdbcOutboxRepository(Verbindungen verbindungen) {
        this.verbindungen = verbindungen;
    }

    @Override
    public void journalEinfuegen(AuftragsId auftragsId, String payloadJson, OffsetDateTime erstelltAm) {
        Connection verbindung = null;
        try {
            verbindung = verbindungen.oeffnen();
            try (var ps = verbindung.prepareStatement(INSERT)) {
                ps.setObject(1, auftragsId.wert());
                ps.setString(2, payloadJson);
                ps.setObject(3, erstelltAm);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Journaleintrag nicht einfuegbar (auftragsId=%s)".formatted(auftragsId.wert()), e);
        } finally {
            verbindungen.schliessen(verbindung);
        }
    }
}
