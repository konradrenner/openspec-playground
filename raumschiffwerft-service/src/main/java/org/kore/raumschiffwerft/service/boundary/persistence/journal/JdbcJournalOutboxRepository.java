package org.kore.raumschiffwerft.service.boundary.persistence.journal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import org.kore.raumschiffwerft.service.control.journal.Journaleintrag;
import org.kore.raumschiffwerft.service.control.journal.JournalOutboxRepository;

/**
 * JDBC-Implementierung des Journal-Outbox-Ports: Ein Relay-Durchlauf
 * laeuft in EINER kurzen Transaktion - Zeilen mit FOR UPDATE SKIP LOCKED
 * beanspruchen, je Zeile den Versand des Relays aufrufen, danach als
 * gesendet markieren und committen. Ein Fehler des Versands rollt die
 * Transaktion zurueck; bereits gesendete Zeilen werden spaeter erneut
 * gesendet (Duplikate sind erlaubt).
 */
@ApplicationScoped
public class JdbcJournalOutboxRepository implements JournalOutboxRepository {

    private static final String UNGESENDETE_LESEN =
            "SELECT id, auftrags_id::text, payload::text, payload->>'traceparent' FROM journal_outbox "
                    + "WHERE gesendet_am IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED";

    private static final String GESENDET_MARKIEREN =
            "UPDATE journal_outbox SET gesendet_am = ? WHERE id = ?";

    private final AgroalDataSource dataSource;

    @Inject
    public JdbcJournalOutboxRepository(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int versenden(int batch, Versand versand) {
        try (Connection verbindung = dataSource.getConnection()) {
            verbindung.setAutoCommit(false);
            try {
                List<Journaleintrag> eintraege = ungesendeteLesen(verbindung, batch);
                OffsetDateTime gesendetAm = OffsetDateTime.now();
                for (Journaleintrag eintrag : eintraege) {
                    versand.senden(eintrag);
                    gesendetMarkieren(verbindung, eintrag.id(), gesendetAm);
                }
                verbindung.commit();
                return eintraege.size();
            } catch (Exception e) {
                try {
                    verbindung.rollback();
                } catch (SQLException rollbackFehler) {
                    e.addSuppressed(rollbackFehler);
                }
                if (e instanceof RuntimeException laufzeit) {
                    throw laufzeit;
                }
                throw new IllegalStateException("Journal-Outbox-Durchlauf gescheitert", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Journal-Outbox nicht erreichbar", e);
        }
    }

    private List<Journaleintrag> ungesendeteLesen(Connection verbindung, int batch) throws SQLException {
        List<Journaleintrag> eintraege = new ArrayList<>();
        try (PreparedStatement ps = verbindung.prepareStatement(UNGESENDETE_LESEN)) {
            ps.setInt(1, batch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    eintraege.add(new Journaleintrag(rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getString(4)));
                }
            }
        }
        return List.copyOf(eintraege);
    }

    private void gesendetMarkieren(Connection verbindung, long id, OffsetDateTime gesendetAm)
            throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(GESENDET_MARKIEREN)) {
            ps.setObject(1, gesendetAm);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }
}
