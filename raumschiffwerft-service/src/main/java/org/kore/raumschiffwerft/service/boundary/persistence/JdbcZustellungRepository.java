package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.service.control.Beanspruchung;
import org.kore.raumschiffwerft.service.control.ZustellungRepository;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;

/**
 * Persistiert Zustellungen per plain JDBC: Anlegen im Rahmen der
 * Annahme-Transaktion (gemeinsame Verbindung), genau ein konditionales
 * Verbuchen nach dem Zustell- bzw. Abgleichsschritt und das atomare
 * Beanspruchen faelliger Zustellungen fuer die Abgleich-Route.
 */
@ApplicationScoped
public class JdbcZustellungRepository implements ZustellungRepository {

    private static final String INSERT =
            "INSERT INTO zustellung (auftrags_id, zielsystem, status, versuche, lease_bis, instanz, aktualisiert_am) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    /** Genau EIN Update per Primaerschluessel, nur wenn die Zeile noch den erwarteten Ausgangsstatus hat. */
    private static final String VERBUCHEN =
            "UPDATE zustellung SET status = ?, externe_referenz = ?, versuche = ?, "
                    + "naechster_versuch_um = ?, aktualisiert_am = now() "
                    + "WHERE auftrags_id = ? AND zielsystem = ? AND status = ?";

    /**
     * Beansprucht faellige Zustellungen atomar: FOR UPDATE SKIP LOCKED
     * verhindert doppelte Bearbeitung durch konkurrierende Instanzen,
     * RETURNING liefert die beanspruchten Zeilen, der Join den kanonischen
     * Auftrag. Ausgangsstatus und -versuche kommen aus dem selektierten
     * Vorzustand, sodass das Aggregat denselben Uebergang vollzieht wie
     * das Statement. Die kurze Transaktion wird sofort committet, damit
     * die neue Lease sichtbar ist, bevor verarbeitet wird.
     */
    private static final String BEANSPRUCHEN =
            "WITH frei AS ("
                    + "  SELECT auftrags_id, zielsystem, status, versuche FROM zustellung"
                    + "  WHERE (status = 'UNGEKLAERT' AND naechster_versuch_um <= now())"
                    + "     OR (status IN ('IN_ZUSTELLUNG', 'IN_ABGLEICH') AND lease_bis < now())"
                    + "  ORDER BY auftrags_id, zielsystem"
                    + "  LIMIT ?"
                    + "  FOR UPDATE SKIP LOCKED"
                    + "), beansprucht AS ("
                    + "  UPDATE zustellung z SET status = 'IN_ABGLEICH',"
                    + "        lease_bis = ?, versuche = z.versuche + 1, instanz = ?,"
                    + "        aktualisiert_am = now()"
                    + "  WHERE (z.auftrags_id, z.zielsystem) IN (SELECT auftrags_id, zielsystem FROM frei)"
                    + "  RETURNING z.auftrags_id, z.zielsystem, z.lease_bis, z.instanz, z.aktualisiert_am"
                    + ")"
                    + "SELECT b.auftrags_id, b.zielsystem, b.lease_bis, b.instanz, b.aktualisiert_am, "
                    + "       f.status, f.versuche, a.kaufauftrag "
                    + "FROM beansprucht b "
                    + "JOIN frei f ON f.auftrags_id = b.auftrags_id AND f.zielsystem = b.zielsystem "
                    + "JOIN auftrag a ON a.auftrags_id = b.auftrags_id "
                    + "ORDER BY b.auftrags_id";

    private final Verbindungen verbindungen;
    private final AgroalDataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public JdbcZustellungRepository(Verbindungen verbindungen, AgroalDataSource dataSource,
                                    ObjectMapper objectMapper) {
        this.verbindungen = verbindungen;
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public void anlegen(Zustellung zustellung) {
        Connection verbindung = null;
        try {
            verbindung = verbindungen.oeffnen();
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
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Anlegen der Zustellung gescheitert (auftragsId=%s, zielsystem=%s)"
                            .formatted(zustellung.auftragsId().wert(), zustellung.zielsystem()), e);
        } finally {
            verbindungen.schliessen(verbindung);
        }
    }

    @Override
    public int verbuchen(Zustellung zustellung, Zustellungsstatus erwarteterAusgangsstatus) {
        try (Connection verbindung = dataSource.getConnection();
                var ps = verbindung.prepareStatement(VERBUCHEN)) {
            ps.setString(1, zustellung.status().name());
            ps.setString(2, zustellung.externeReferenz());
            ps.setInt(3, zustellung.versuche());
            ps.setObject(4, zustellung.naechsterVersuchUm());
            ps.setObject(5, zustellung.auftragsId().wert());
            ps.setString(6, zustellung.zielsystem().name());
            ps.setString(7, erwarteterAusgangsstatus.name());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Verbuchen der Zustellung gescheitert (auftragsId=%s, zielsystem=%s)"
                            .formatted(zustellung.auftragsId().wert(), zustellung.zielsystem()), e);
        }
    }

    @Override
    public List<Beanspruchung> faelligeBeanspruchen(int batch, OffsetDateTime leaseBis, String instanz) {
        try (Connection verbindung = dataSource.getConnection()) {
            verbindung.setAutoCommit(false);
            try (var ps = verbindung.prepareStatement(BEANSPRUCHEN)) {
                ps.setInt(1, batch);
                ps.setObject(2, leaseBis);
                ps.setString(3, instanz);
                List<Beanspruchung> beansprucht = new ArrayList<>();
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AuftragsId auftragsId = new AuftragsId(rs.getObject(1, java.util.UUID.class));
                        org.kore.raumschiffwerft.model.entity.Zielsystemtyp zielsystem =
                                org.kore.raumschiffwerft.model.entity.Zielsystemtyp.valueOf(rs.getString(2));
                        Zustellungsstatus ausgangsstatus = Zustellungsstatus.valueOf(rs.getString(6));
                        Zustellung zustellung = new Zustellung(auftragsId, zielsystem, ausgangsstatus,
                                null, rs.getInt(7), null, null, null,
                                rs.getObject(5, OffsetDateTime.class));
                        zustellung.beanspruchen(rs.getObject(3, OffsetDateTime.class), rs.getString(4));
                        beansprucht.add(new Beanspruchung(zustellung, ausgangsstatus,
                                objectMapper.readValue(rs.getString(8),
                                        org.kore.raumschiffwerft.model.entity.Kaufauftrag.class)));
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(
                            "Kanonischer Auftrag einer beanspruchten Zustellung nicht lesbar", e);
                }
                verbindung.commit();
                return List.copyOf(beansprucht);
            } catch (SQLException | RuntimeException e) {
                try {
                    verbindung.rollback();
                } catch (SQLException rollbackFehler) {
                    e.addSuppressed(rollbackFehler);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Beanspruchen faelliger Zustellungen gescheitert", e);
        }
    }
}
