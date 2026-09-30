package org.kore.raumschiffwerft.service.control.journal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest und markiert Outbox-Zeilen der Komponente journal per plain
 * JDBC. Der Zugriff laeuft in der Transaktion des Relays (Connection
 * von aussen), damit FOR UPDATE SKIP LOCKED die beanspruchten Zeilen
 * bis zum Commit gegenueber konkurrierenden Instanzen sperrt.
 */
public class JournalOutboxRepository {

    private static final String UNGESENDETE_LESEN =
            "SELECT id, auftrags_id::text, payload::text FROM journal_outbox "
                    + "WHERE gesendet_am IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED";

    private static final String GESENDET_MARKIEREN =
            "UPDATE journal_outbox SET gesendet_am = ? WHERE id = ?";

    /**
     * Liest bis zu batch ungesendete Zeilen fuer Update gesperrt; die
     * Sperren gelten bis zum Commit der uebergebenen Verbindung.
     */
    public List<Journaleintrag> ungesendeteLesen(Connection verbindung, int batch) throws SQLException {
        List<Journaleintrag> eintraege = new ArrayList<>();
        try (PreparedStatement ps = verbindung.prepareStatement(UNGESENDETE_LESEN)) {
            ps.setInt(1, batch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    eintraege.add(new Journaleintrag(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        return List.copyOf(eintraege);
    }

    /** Markiert eine Zeile als gesendet; ohne Commit bleibt sie ungesendet. */
    public void gesendetMarkieren(Connection verbindung, long id, OffsetDateTime gesendetAm)
            throws SQLException {
        try (PreparedStatement ps = verbindung.prepareStatement(GESENDET_MARKIEREN)) {
            ps.setObject(1, gesendetAm);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }
}
