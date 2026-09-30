package org.kore.raumschiffwerft.service.boundary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrationstest der Beobachtbarkeit: Die Annahme speichert auch ohne
 * Client-traceparent eine Trace-ID (aus dem Server-Span), die Span- und
 * Ergebnis-Metriken sind in der MeterRegistry sichtbar.
 */
@QuarkusTest
class ObservabilityIT {

    @Inject
    AgroalDataSource dataSource;

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void annahmeOhneTraceparentSpeichertTrotzdemEineTraceId() throws SQLException {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body("{\"kaeufer\": \"Tarkin\", \"klasse\": \"IMPERIAL_I\", \"anzahl\": 2, \"lieferplanet\": 42}")
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(200);

        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT trace_id FROM auftrag WHERE auftrags_id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String traceId = rs.getString(1);
                assertNotNull(traceId, "trace_id muss auch ohne Client-traceparent gesetzt sein");
                assertTrue(traceId.matches("[0-9a-f]{32}"),
                        "trace_id ist keine Trace-ID: " + traceId);
            }
        }
    }

    @Test
    void ergebnisUndBetriebsmetrikenSindInDerRegistry() {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body("{\"kaeufer\": \"Tarkin\", \"klasse\": \"IMPERIAL_I\", \"anzahl\": 2, \"lieferplanet\": 42}")
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(200);

        assertTrue(meterRegistry.get("durchlauferhitzer.zustellungen")
                .tag("zielsystem", "IMPERIUM").tag("ergebnis", "BESTAETIGT").counter().count() >= 1);
        assertNotNull(meterRegistry.get("durchlauferhitzer.zustellungen.offen").gauge());
        assertNotNull(meterRegistry.get("durchlauferhitzer.zustellungen.fehlgeschlagen").gauge());
        assertNotNull(meterRegistry.get("durchlauferhitzer.outbox.rueckstand").gauge());
    }
}
