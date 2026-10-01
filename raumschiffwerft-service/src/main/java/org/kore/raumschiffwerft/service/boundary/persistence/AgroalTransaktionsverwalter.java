package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import org.kore.raumschiffwerft.service.control.DatenbankNichtErreichbar;
import org.kore.raumschiffwerft.service.control.Transaktionsverwalter;

/**
 * Fuehrt Arbeit des control in EINER Datenbank-Transaktion aus: Verbindung
 * oeffnen, setAutoCommit(false), Arbeit ausfuehren, committen - bei jedem
 * Fehler zurueckrollen und als IllegalStateException melden. Ist die
 * Datenbank nicht erreichbar, wird DatenbankNichtErreichbar gemeldet.
 * Die Verbindung wird waehrend der Transaktion ueber die Verbindungen den
 * JDBC-Implementierungen im selben Paket zur Verfuegung gestellt.
 */
@ApplicationScoped
public class AgroalTransaktionsverwalter implements Transaktionsverwalter {

    private final AgroalDataSource dataSource;
    private final Verbindungen verbindungen;

    @Inject
    public AgroalTransaktionsverwalter(AgroalDataSource dataSource, Verbindungen verbindungen) {
        this.dataSource = dataSource;
        this.verbindungen = verbindungen;
    }

    @Override
    public <T> T inTransaktion(Arbeit<T> arbeit) {
        Connection verbindung;
        try {
            verbindung = dataSource.getConnection();
        } catch (SQLException | RuntimeException e) {
            throw new DatenbankNichtErreichbar(e);
        }
        try (verbindung) {
            verbindung.setAutoCommit(false);
            verbindungen.setzen(verbindung);
            try {
                T ergebnis = arbeit.ausfuehren();
                verbindung.commit();
                return ergebnis;
            } catch (Exception e) {
                try {
                    verbindung.rollback();
                } catch (SQLException rollbackFehler) {
                    e.addSuppressed(rollbackFehler);
                }
                throw new IllegalStateException("Transaktion gescheitert", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Transaktion nicht durchfuehrbar", e);
        } finally {
            verbindungen.entfernen();
        }
    }
}
