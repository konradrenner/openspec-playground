package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import org.kore.raumschiffwerft.service.control.Bestandszaehler;

/**
 * Zaehlt die Bestaende der Betriebsmetriken per plain JDBC. Gezaehlt wird
 * bei Abfrage (kein Poll-Thread); ist die Zaehlung nicht moeglich, liefert
 * jede Operation 0 und es wird eine Warnung geloggt.
 */
@ApplicationScoped
public class JdbcBestandszaehler implements Bestandszaehler {

    private static final Logger LOG = Logger.getLogger(JdbcBestandszaehler.class.getName());

    private static final String OFFENE_ZUSTELLUNGEN =
            "SELECT count(*) FROM zustellung "
                    + "WHERE status IN ('IN_ZUSTELLUNG', 'IN_ABGLEICH', 'UNGEKLAERT')";
    private static final String FEHLGESCHLAGENE_ZUSTELLUNGEN =
            "SELECT count(*) FROM zustellung WHERE status = 'FEHLGESCHLAGEN'";
    private static final String OUTBOX_RUECKSTAND =
            "SELECT count(*) FROM journal_outbox WHERE gesendet_am IS NULL";

    private final AgroalDataSource dataSource;

    @Inject
    public JdbcBestandszaehler(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long offeneZustellungen() {
        return zaehlen(OFFENE_ZUSTELLUNGEN);
    }

    @Override
    public long fehlgeschlageneZustellungen() {
        return zaehlen(FEHLGESCHLAGENE_ZUSTELLUNGEN);
    }

    @Override
    public long outboxRueckstand() {
        return zaehlen(OUTBOX_RUECKSTAND);
    }

    private long zaehlen(String sql) {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement ps = verbindung.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Betriebsmetriken: Zaehlung nicht moeglich ({0})", sql);
            return 0;
        }
    }
}
