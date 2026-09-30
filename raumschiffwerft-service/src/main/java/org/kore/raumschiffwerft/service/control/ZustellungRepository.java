package org.kore.raumschiffwerft.service.control;

import java.sql.Connection;
import java.sql.SQLException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kore.raumschiffwerft.service.entity.Zustellung;
import io.agroal.api.AgroalDataSource;

/**
 * Persistiert Zustellungen per plain JDBC: Anlegen im Rahmen der
 * Annahme-Transaktion und genau ein konditionales Verbuchen nach dem
 * Zustellversuch.
 */
@ApplicationScoped
public class ZustellungRepository {

    private static final String INSERT =
            "INSERT INTO zustellung (auftrags_id, zielsystem, status, versuche, lease_bis, instanz, aktualisiert_am) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    /**
     * Genau EIN Update per Primaerschluessel, und nur wenn die Zeile noch
     * den erwarteten Ausgangsstatus IN_ZUSTELLUNG hat.
     */
    private static final String VERBUCHEN =
            "UPDATE zustellung SET status = ?, externe_referenz = ?, versuche = ?, "
                    + "naechster_versuch_um = ?, aktualisiert_am = now() "
                    + "WHERE auftrags_id = ? AND zielsystem = ? AND status = 'IN_ZUSTELLUNG'";

    private final AgroalDataSource dataSource;

    @Inject
    public ZustellungRepository(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Legt die Zustellungszeile im Rahmen der Annahme-Transaktion an. */
    public void anlegen(Connection verbindung, Zustellung zustellung) throws SQLException {
        try (var ps = verbindung.prepareStatement(INSERT)) {
            ps.setObject(1, zustellung.auftragsId().wert());
            ps.setString(2, zustellung.zielsystem().name());
            ps.setString(3, zustellung.status().name());
            ps.setInt(4, zustellung.versuche());
            ps.setObject(5, zustellung.leaseBis());
            ps.setString(6, zustellung.instanz());
            ps.setObject(7, zustellung.aktualisiertAm());
            ps.executeUpdate();
        }
    }

    /**
     * Verbucht den Ausgang des Zustellversuchs. Rueckgabe 0 heisst: die
     * Zeile hatte nicht mehr den erwarteten Ausgangsstatus, es wurde
     * nichts geaendert.
     */
    public int verbuchen(Zustellung zustellung) throws SQLException {
        try (Connection verbindung = dataSource.getConnection();
                var ps = verbindung.prepareStatement(VERBUCHEN)) {
            ps.setString(1, zustellung.status().name());
            ps.setString(2, zustellung.externeReferenz());
            ps.setInt(3, zustellung.versuche());
            ps.setObject(4, zustellung.naechsterVersuchUm());
            ps.setObject(5, zustellung.auftragsId().wert());
            ps.setString(6, zustellung.zielsystem().name());
            return ps.executeUpdate();
        }
    }
}
