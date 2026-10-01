package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;

/**
 * Verbindungszugriff der JDBC-Implementierungen: Innerhalb einer laufenden
 * Transaktion (Transaktionsverwalter) liefert oeffnen() die gemeinsam
 * genutzte Transaktionsverbindung, sonst eine eigene. schliessen() schliesst
 * nur eigene Verbindungen - die Transaktionsverbindung gehoert dem
 * Transaktionsverwalter.
 */
@ApplicationScoped
class Verbindungen {

    private final AgroalDataSource dataSource;

    private final ThreadLocal<Connection> transaktionsVerbindung = new ThreadLocal<>();

    @Inject
    Verbindungen(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Setzt die Verbindung der laufenden Transaktion (nur Transaktionsverwalter). */
    void setzen(Connection verbindung) {
        transaktionsVerbindung.set(verbindung);
    }

    /** Entfernt die Transaktionsverbindung (finally des Transaktionsverwalters). */
    void entfernen() {
        transaktionsVerbindung.remove();
    }

    /** Transaktionsverbindung, wenn eine besteht, sonst eine eigene. */
    Connection oeffnen() throws SQLException {
        Connection verbindung = transaktionsVerbindung.get();
        return verbindung != null ? verbindung : dataSource.getConnection();
    }

    /** Schliesst die Verbindung, sofern sie nicht der Transaktion gehoert. */
    void schliessen(Connection verbindung) {
        if (verbindung == null || verbindung == transaktionsVerbindung.get()) {
            return;
        }
        try {
            verbindung.close();
        } catch (SQLException e) {
            // Beim Schliessen einer eigenen Verbindung ist nichts mehr zu retten.
        }
    }
}
