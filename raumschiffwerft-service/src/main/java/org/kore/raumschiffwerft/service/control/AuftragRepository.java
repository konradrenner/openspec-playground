package org.kore.raumschiffwerft.service.control;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.service.entity.Kaufauftrag;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;

/**
 * Persistiert und liest Auftraege per plain JDBC.
 */
@ApplicationScoped
public class AuftragRepository {

    private static final String INSERT =
            "INSERT INTO auftrag (auftrags_id, kaufauftrag, schema_version, trace_id, angenommen_am) "
                    + "VALUES (?, ?::jsonb, ?, ?, ?) ON CONFLICT (auftrags_id) DO NOTHING";

    private static final String SELECT_AUFTRAG =
            "SELECT kaufauftrag, angenommen_am FROM auftrag WHERE auftrags_id = ?";

    private static final String SELECT_ZUSTELLUNGEN =
            "SELECT zielsystem, status, externe_referenz, versuche, naechster_versuch_um, "
                    + "lease_bis, instanz, aktualisiert_am "
                    + "FROM zustellung WHERE auftrags_id = ? ORDER BY zielsystem";

    private final AgroalDataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public AuftragRepository(AgroalDataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Legt den Auftrag an; ON CONFLICT DO NOTHING dient als
     * Idempotenz-Check. Rueckgabe 0 heisst: Auftrag existierte bereits.
     */
    public int anlegen(Connection verbindung, AuftragsId auftragsId, String kaufauftragJson,
                      int schemaVersion, String traceId, OffsetDateTime angenommenAm) throws SQLException {
        try (var ps = verbindung.prepareStatement(INSERT)) {
            ps.setObject(1, auftragsId.wert());
            ps.setString(2, kaufauftragJson);
            ps.setInt(3, schemaVersion);
            ps.setString(4, traceId);
            ps.setObject(5, angenommenAm);
            return ps.executeUpdate();
        }
    }

    /**
     * Liest den vollstaendigen Stand (auftrag samt zustellung-Zeilen);
     * leer, wenn die AuftragsId unbekannt ist.
     */
    public Optional<Kaufauftrag> stand(AuftragsId auftragsId) throws SQLException {
        try (Connection verbindung = dataSource.getConnection()) {
            OffsetDateTime angenommenAm;
            org.kore.raumschiffwerft.model.entity.Kaufauftrag kanonisch;
            try (var ps = verbindung.prepareStatement(SELECT_AUFTRAG)) {
                ps.setObject(1, auftragsId.wert());
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    kanonisch = objectMapper.readValue(rs.getString(1),
                            org.kore.raumschiffwerft.model.entity.Kaufauftrag.class);
                    angenommenAm = rs.getObject(2, OffsetDateTime.class);
                }
            }
            List<Zustellung> zustellungen = new ArrayList<>();
            try (var ps = verbindung.prepareStatement(SELECT_ZUSTELLUNGEN)) {
                ps.setObject(1, auftragsId.wert());
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        zustellungen.add(new Zustellung(auftragsId,
                                org.kore.raumschiffwerft.model.entity.Zielsystemtyp.valueOf(rs.getString(1)),
                                Zustellungsstatus.valueOf(rs.getString(2)),
                                rs.getString(3),
                                rs.getInt(4),
                                rs.getObject(5, OffsetDateTime.class),
                                rs.getObject(6, OffsetDateTime.class),
                                rs.getString(7),
                                rs.getObject(8, OffsetDateTime.class)));
                    }
                }
            }
            return Optional.of(new Kaufauftrag(auftragsId, kanonisch, angenommenAm, zustellungen));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Kanonischer Auftrag nicht lesbar", e);
        }
    }
}
