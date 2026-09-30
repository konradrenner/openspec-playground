package org.kore.raumschiffwerft.service.boundary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integrationstest der Journal-Kette gegen die devenv-Dienste: Annahme
 * mit traceparent-Header → Outbox-Relay versendet nach Kafka →
 * Consumer-Route indexiert in OpenSearch. Der Journaleintrag muss mit
 * der Trace-ID im Index erscheinen und in der Outbox als gesendet
 * markiert sein.
 */
@QuarkusTest
class JournalRelayIT {

    private static final String OPENSEARCH = "http://localhost:9200";
    private static final String INDEX = "durchlauferhitzer-journal";

    @Inject
    AgroalDataSource dataSource;

    @Test
    void journaleintragMitTraceIdErscheintInOpenSearch() throws SQLException {
        UUID id = UUID.randomUUID();
        String traceId = "0af7651916cd43dd8448eb4c9cbbfda1";
        String traceparent = "00-%s-0af7651916cd43dd-01".formatted(traceId);

        given()
                .header("Idempotency-Key", id)
                .header("traceparent", traceparent)
                .header("Content-Type", "application/json")
                .body("{\"kaeufer\": \"Tarkin\", \"klasse\": \"IMPERIAL_I\", \"anzahl\": 2, \"lieferplanet\": 42}")
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(200);

        // Relay → Kafka → Consumer → OpenSearch (Dokument-ID = Auftrags-ID)
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> given()
                        .when().get(OPENSEARCH + "/" + INDEX + "/_doc/" + id)
                        .then()
                        .statusCode(200)
                        .body("_source.traceId", equalTo(traceId))
                        .body("_source.auftragsId", equalTo(id.toString()))
                        .body("_source.kaufauftrag.kaeufer", equalTo("Tarkin")));

        // die Outbox-Zeile ist als gesendet markiert
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT gesendet_am FROM journal_outbox WHERE auftrags_id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(rs.next());
                org.junit.jupiter.api.Assertions.assertNotNull(rs.getObject(1, java.time.OffsetDateTime.class));
            }
        }
    }

    @Test
    void indexExistiertMitExplizitemMappingOhneRohPayloadIndizierung() {
        given()
                .when().get(OPENSEARCH + "/" + INDEX + "/_mapping")
                .then()
                .statusCode(200)
                .body(INDEX + ".mappings.properties.auftragsId.type", equalTo("keyword"))
                .body(INDEX + ".mappings.properties.traceId.type", equalTo("keyword"))
                .body(INDEX + ".mappings.properties.rohPayload.enabled", equalTo(false))
                .body(INDEX + ".mappings.properties", notNullValue());
    }
}
