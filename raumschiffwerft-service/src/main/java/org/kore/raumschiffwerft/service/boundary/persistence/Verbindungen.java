package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

/**
 * Verbindungszugriff der JDBC-Implementierungen: Innerhalb einer laufenden
 * Transaktion (Transaktionsverwalter) liefert oeffnen() die gemeinsam
 * genutzte Transaktionsverbindung, sonst eine eigene. schliessen() schliesst
 * nur eigene Verbindungen - die Transaktionsverbindung gehoert dem
 * Transaktionsverwalter. Jede gelieferte Verbindung ist ein
 * GetracedVerbindung-Wrapper: jede Statement-Ausfuehrung oeffnet einen
 * eigenen db.zugriff-Span.
 */
@ApplicationScoped
class Verbindungen {

    private final AgroalDataSource dataSource;
    private final Tracer tracer;

    private final ThreadLocal<Connection> transaktionsVerbindung = new ThreadLocal<>();

    @Inject
    Verbindungen(AgroalDataSource dataSource, OpenTelemetry openTelemetry) {
        this.dataSource = dataSource;
        this.tracer = GetracedVerbindung.tracer(openTelemetry);
    }

    /**
     * Setzt die Verbindung der laufenden Transaktion (nur Transaktionsverwalter).
     * Die Verbindung wird gewrappt; die Identitaet des Wrappers ist die, die
     * schliessen() vergleicht - der Transaktionsverwalter arbeitet mit der
     * rohen Verbindung weiter.
     */
    void setzen(Connection verbindung) {
        transaktionsVerbindung.set(GetracedVerbindung.verbinden(verbindung, tracer));
    }

    /** Entfernt die Transaktionsverbindung (finally des Transaktionsverwalters). */
    void entfernen() {
        transaktionsVerbindung.remove();
    }

    /** Transaktionsverbindung, wenn eine besteht, sonst eine eigene. */
    Connection oeffnen() throws SQLException {
        Connection verbindung = transaktionsVerbindung.get();
        return verbindung != null ? verbindung
                : GetracedVerbindung.verbinden(dataSource.getConnection(), tracer);
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
