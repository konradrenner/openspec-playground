package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kore.raumschiffwerft.service.control.aufraeumen.AuftragKandidat;
import org.kore.raumschiffwerft.service.control.aufraeumen.Aufraeumung;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import io.agroal.api.AgroalDataSource;

/**
 * SQL-Seite des Aufraeumens (plain JDBC): Advisory-Lock auf einer
 * eigenen, fuer den gesamten Durchlauf gehaltenen Connection
 * (Advisory-Locks sind sessiongebunden), Batchloeschung gesendeter
 * Journal-Zeilen, Kandidatensuche mit NOT EXISTS-Prefilter und die
 * Loeschung von Auftraegen samt Zustellungszeilen in einer Transaktion.
 */
@ApplicationScoped
public class AufraeumRepository implements Aufraeumung {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(AufraeumRepository.class.getName());

    /** Fester Advisory-Lock-Schluessel des Aufraeumens ("RAUM" in Hex). */
    private static final long ADVISORY_LOCK_SCHLUESSEL = 0x5241554DL;

    private static final String SPERREN = "SELECT pg_try_advisory_lock(?)";
    private static final String ENTSPERREN = "SELECT pg_advisory_unlock(?)";
    private static final String JOURNAL_LOESCHEN =
            "DELETE FROM journal_outbox WHERE id IN ("
                    + "SELECT id FROM journal_outbox WHERE gesendet_am IS NOT NULL "
                    + "ORDER BY id LIMIT ?)";
    private static final String KANDIDATEN =
            "SELECT a.auftrags_id, a.angenommen_am, array_agg(z.status ORDER BY z.zielsystem) "
                    + "FROM auftrag a JOIN zustellung z ON z.auftrags_id = a.auftrags_id "
                    + "WHERE a.angenommen_am < ? "
                    + "AND NOT EXISTS (SELECT 1 FROM zustellung o "
                    + "WHERE o.auftrags_id = a.auftrags_id AND o.status <> 'BESTAETIGT') "
                    + "GROUP BY a.auftrags_id, a.angenommen_am "
                    + "LIMIT ?";

    private final AgroalDataSource dataSource;

    /** Waehrend des Durchlaufs gesperrte Connection; ausserhalb null. */
    private Connection verbindung;

    @Inject
    public AufraeumRepository(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean sperren() {
        try {
            verbindung = dataSource.getConnection();
            try (PreparedStatement ps = verbindung.prepareStatement(SPERREN)) {
                ps.setLong(1, ADVISORY_LOCK_SCHLUESSEL);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getBoolean(1)) {
                        return true;
                    }
                }
            }
            LOG.info("Aufraeumen: ein anderer Pod haelt die Sperre, Durchlauf abgebrochen");
            verbindungSchliessen();
            return false;
        } catch (SQLException e) {
            verbindungSchliessen();
            throw new IllegalStateException("Aufraeumen: Sperre nicht setzbar", e);
        }
    }

    @Override
    public void gesendeteJournalZeilenLoeschen(int batch) {
        try (PreparedStatement ps = verbindung.prepareStatement(JOURNAL_LOESCHEN)) {
            ps.setInt(1, batch);
            int geloescht = ps.executeUpdate();
            if (geloescht > 0) {
                LOG.log(java.util.logging.Level.INFO,
                        "Aufraeumen: {0} gesendete Journal-Zeilen geloescht", geloescht);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Aufraeumen: Journal-Loeschung gescheitert", e);
        }
    }

    @Override
    public List<AuftragKandidat> kandidaten(OffsetDateTime stichtag, int batch) {
        try (PreparedStatement ps = verbindung.prepareStatement(KANDIDATEN)) {
            ps.setObject(1, stichtag);
            ps.setInt(2, batch);
            List<AuftragKandidat> kandidaten = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    kandidaten.add(new AuftragKandidat(
                            rs.getObject(1, UUID.class),
                            rs.getObject(2, OffsetDateTime.class),
                            zustaende(rs.getArray(3))));
                }
            }
            return List.copyOf(kandidaten);
        } catch (SQLException e) {
            throw new IllegalStateException("Aufraeumen: Kandidatensuche gescheitert", e);
        }
    }

    @Override
    public void auftraegeLoeschen(List<UUID> auftragsIds) {
        try {
            verbindung.setAutoCommit(false);
            try (PreparedStatement zustellungen = verbindung.prepareStatement(
                    "DELETE FROM zustellung WHERE auftrags_id = ?");
                 PreparedStatement auftraege = verbindung.prepareStatement(
                         "DELETE FROM auftrag WHERE auftrags_id = ?")) {
                for (UUID id : auftragsIds) {
                    zustellungen.setObject(1, id);
                    zustellungen.executeUpdate();
                    auftraege.setObject(1, id);
                    auftraege.executeUpdate();
                }
                verbindung.commit();
            } catch (SQLException e) {
                try {
                    verbindung.rollback();
                } catch (SQLException rollbackFehler) {
                    e.addSuppressed(rollbackFehler);
                }
                throw e;
            } finally {
                verbindung.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Aufraeumen: Auftrags-Loeschung gescheitert", e);
        }
    }

    @Override
    public void entsperren() {
        try {
            if (verbindung != null) {
                try (PreparedStatement ps = verbindung.prepareStatement(ENTSPERREN)) {
                    ps.setLong(1, ADVISORY_LOCK_SCHLUESSEL);
                    ps.execute();
                }
            }
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Aufraeumen: Sperre nicht freigegeben (Connection schliesst trotzdem)", e);
        } finally {
            verbindungSchliessen();
        }
    }

    private void verbindungSchliessen() {
        try {
            if (verbindung != null) {
                verbindung.close();
            }
        } catch (SQLException e) {
            LOG.log(java.util.logging.Level.WARNING, "Aufraeumen: Connection nicht schliessbar", e);
        } finally {
            verbindung = null;
        }
    }

    private List<Zustellungsstatus> zustaende(Array sqlArray) throws SQLException {
        List<Zustellungsstatus> zustaende = new ArrayList<>();
        try (ResultSet rs = sqlArray.getResultSet()) {
            while (rs.next()) {
                zustaende.add(Zustellungsstatus.valueOf(rs.getString(2)));
            }
        }
        return List.copyOf(zustaende);
    }
}
