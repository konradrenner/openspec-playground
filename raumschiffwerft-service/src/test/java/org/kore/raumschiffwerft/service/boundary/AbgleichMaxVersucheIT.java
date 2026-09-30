package org.kore.raumschiffwerft.service.boundary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Endgueltigkeits-IT: mit max-versuche 2 (MaxVersucheZweiProfile) wird eine
 * durchgehend scheiternde Zustellung nach dem zweiten Versuch als
 * FEHLGESCHLAGEN verbucht (mit Fehler-Log, siehe Testausgabe); die Zeilen
 * bleiben ungeloescht.
 */
@QuarkusTest
@TestProfile(MaxVersucheZweiProfile.class)
class AbgleichMaxVersucheIT {

    private static final HttpClient ADMIN = HttpClient.newHttpClient();
    private static final URI SZENARIEN_ZURUECKSETZEN =
            URI.create("http://localhost:8089/__admin/scenarios/reset");

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void szenarienZuruecksetzenUndTabelleAufraeumen() throws Exception {
        ADMIN.send(HttpRequest.newBuilder(SZENARIEN_ZURUECKSETZEN)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.discarding());
        try (Connection c = dataSource.getConnection();
                PreparedStatement loeschen = c.prepareStatement(
                        "DELETE FROM zustellung; DELETE FROM journal_outbox; DELETE FROM auftrag")) {
            loeschen.executeUpdate();
        }
    }

    private String body(String kaeufer, int lieferplanet) {
        return """
                {"kaeufer": "%s", "klasse": "IMPERIAL_I", "anzahl": 2, "lieferplanet": %d}"""
                .formatted(kaeufer, lieferplanet);
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

    @Test
    void durchgehendScheiterndeZustellungWirdFehlgeschlagen() throws SQLException {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Jar Jar Binks", 42))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(202)
                .body("zustellungen[0].status", equalTo("UNGEKLAERT"));

        // beansprucht (Versuch 2), Statusabfrage liefert IN_BEARBEITUNG,
        // versuche >= max-versuche -> FEHLGESCHLAGEN mit Fehler-Log
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> given()
                        .when().get("/api/v1/kaufauftraege/" + id)
                        .then()
                        .statusCode(200)
                        .body("zustellungen[0].status", equalTo("FEHLGESCHLAGEN"))
                        .body("zustellungen[0].naechsterVersuchUm", anyOf(nullValue(), equalTo(""))));

        // Zeilen werden nie geloescht
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("auftrag", id));
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("zustellung", id));
    }
}
