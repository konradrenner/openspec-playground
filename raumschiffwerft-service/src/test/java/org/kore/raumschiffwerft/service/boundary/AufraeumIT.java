package org.kore.raumschiffwerft.service.boundary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integrationstest des Aufraeumens gegen die devenv-Postgres: mit
 * Aufbewahrung 1 s (AufraeumenTestProfile) verschwinden die gesendete
 * Journal-Zeile und der abgeschlossene Auftrag samt Zustellung, danach
 * ist derselbe Idempotency-Key wieder eine Neuanname. Ein
 * FEHLGESCHLAGEN-Auftrag bleibt unberuehrt.
 */
@QuarkusTest
@TestProfile(AufraeumenTestProfile.class)
class AufraeumIT {

    @Inject
    AgroalDataSource dataSource;

    private String body(String kaeufer) {
        return "{\"kaeufer\": \"%s\", \"klasse\": \"IMPERIAL_I\", \"anzahl\": 2, \"lieferplanet\": 42}"
                .formatted(kaeufer);
    }

    private int zeilen(String tabelle, UUID auftragsId) throws SQLException {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM " + tabelle + " WHERE auftrags_id = ?")) {
            ps.setObject(1, auftragsId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Legt einen abgelaufenen FEHLGESCHLAGEN-Auftrag direkt per SQL an. */
    private UUID fehlgeschlagenenAuftragAnlegen() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement auftrag = c.prepareStatement(
                    "INSERT INTO auftrag (auftrags_id, kaufauftrag, schema_version, trace_id, angenommen_am) "
                            + "VALUES (?, '{\"kaeufer\": \"Jar Jar Binks\", \"klasse\": \"VICTORY\", "
                            + "\"anzahl\": 1, \"lieferplanet\": 1}'::jsonb, 1, NULL, ?)");
                 PreparedStatement zustellung = c.prepareStatement(
                         "INSERT INTO zustellung (auftrags_id, zielsystem, status, versuche, "
                                 + "naechster_versuch_um, lease_bis, instanz, aktualisiert_am) "
                                 + "VALUES (?, 'IMPERIUM', 'FEHLGESCHLAGEN', 2, NULL, NULL, 'test-pod', now())")) {
                auftrag.setObject(1, id);
                auftrag.setObject(2, OffsetDateTime.now().minusDays(10));
                auftrag.executeUpdate();
                zustellung.setObject(1, id);
                zustellung.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
        return id;
    }

    @Test
    void abgeschlossenerAuftragUndGesendeteJournalZeileVerschwinden() {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Tarkin"))
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(200);

        // Journal-Zeile wird versendet und geloescht
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300))
                .until(() -> zeilen("journal_outbox", id) == 0);
        // Auftrag samt Zustellung nach Ablauf der Aufbewahrung geloescht
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300))
                .until(() -> zeilen("auftrag", id) == 0 && zeilen("zustellung", id) == 0);

        given()
                .when().get("/api/v1/kaufauftraege/" + id)
                .then().statusCode(404);

        // Idempotenzfenster abgelaufen: derselbe Key ist eine Neuanname
        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Tarkin"))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(200)
                .body("zustellungen[0].status", equalTo("BESTAETIGT"))
                .body("zustellungen[0].versuche", equalTo(1));
    }

    @Test
    void fehlgeschlagenerAuftragBleibtFuerImmer() throws SQLException {
        UUID id = fehlgeschlagenenAuftragAnlegen();

        // der Timer laeuft mit 500 ms; zwei Sekunden Stabilitaet reichen
        // als Beweis, dass die Regel ihn nie loescht
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4))
                .until(() -> zeilen("auftrag", id) == 1 && zeilen("zustellung", id) == 1);

        given()
                .when().get("/api/v1/kaufauftraege/" + id)
                .then()
                .statusCode(200)
                .body("zustellungen[0].status", equalTo("FEHLGESCHLAGEN"));
    }
}
