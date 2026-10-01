package org.kore.raumschiffwerft.service.boundary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Integrationstest der Annahme und Abfrage gegen die devenv-Postgres und
 * die WireMock-Stubs der Adapter (Imperium: ISD-4711, Rebellion: RB-1138,
 * Jar Jar: Fehler).
 */
@QuarkusTest
class AnnahmeIT {

    @Inject
    AgroalDataSource dataSource;


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
    void ordnungsgemaesseAnnahmeAnImperiumLiefert200() {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Tarkin", 42))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(200)
                .body("auftragsId", equalTo(id.toString()))
                .body("kaufauftrag.kaeufer", equalTo("Tarkin"))
                .body("zustellungen.size()", equalTo(1))
                .body("zustellungen[0].zielsystem", equalTo("IMPERIUM"))
                .body("zustellungen[0].status", equalTo("BESTAETIGT"))
                .body("zustellungen[0].externeReferenz", equalTo("ISD-4711"))
                .body("zustellungen[0].versuche", equalTo(1));

        given()
                .when().get("/api/v1/kaufauftraege/" + id)
                .then()
                .statusCode(200)
                .body("zustellungen[0].status", equalTo("BESTAETIGT"));
    }

    @Test
    void lieferplanet4WaehltRebellion() {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Leia Organa", 4))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(200)
                .body("zustellungen[0].zielsystem", equalTo("REBELLION"))
                .body("zustellungen[0].externeReferenz", equalTo("RB-1138"))
                .body("zustellungen[0].status", equalTo("BESTAETIGT"));
    }

    @Test
    void ungeklaerteZustellungLiefert202MitLocation() throws SQLException {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Jar Jar Binks", 42))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(202)
                .header("Location", equalTo("/api/v1/kaufauftraege/" + id))
                .body("zustellungen[0].status", equalTo("UNGEKLAERT"))
                .body("zustellungen[0].naechsterVersuchUm", not(nullValue()))
                .body("zustellungen[0].externeReferenz", nullValue());

        given()
                .when().get("/api/v1/kaufauftraege/" + id)
                .then()
                .statusCode(200)
                .body("zustellungen[0].status", equalTo("UNGEKLAERT"))
                .body("zustellungen[0].naechsterVersuchUm", not(emptyOrNullString()));

        // genau eine Zeile je Tabelle, Zeilen werden nie geloescht
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("auftrag", id));
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("zustellung", id));
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("journal_outbox", id));
    }

    @Test
    void wiederholteAnnahmeSchreibtKeinDuplikat() throws SQLException {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Tarkin", 42))
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(200);

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Tarkin", 42))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(200)
                .body("zustellungen[0].versuche", equalTo(1)); // keine zweite Zustellung

        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("auftrag", id));
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("zustellung", id));
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("journal_outbox", id));

        // Journaleintrag existiert genau einmal; der Versand laeuft asynchron
        // ueber den Journal-Relay (gesendet_am siehe JournalRelayIT)
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM journal_outbox WHERE auftrags_id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                org.junit.jupiter.api.Assertions.assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void fehlenderIdempotencyKeyLiefert400() throws SQLException {
        UUID id = UUID.randomUUID();

        given()
                .header("Content-Type", "application/json")
                .body(body("Tarkin", 42))
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(400);

        org.junit.jupiter.api.Assertions.assertEquals(0, zeilen("auftrag", id));
    }

    @Test
    void idempotencyKeyOhneUuidLiefert400() {
        given()
                .header("Idempotency-Key", "keine-uuid")
                .header("Content-Type", "application/json")
                .body(body("Tarkin", 42))
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(400);
    }

    @Test
    void ungueltigerBodyLiefert400() {
        given()
                .header("Idempotency-Key", UUID.randomUUID())
                .header("Content-Type", "application/json")
                .body(body("Tarkin", 61)) // lieferplanet ausserhalb 1-60
                .when().post("/api/v1/kaufauftraege")
                .then().statusCode(400);
    }

    @Test
    void unbekannterAuftragLiefert404() {
        given()
                .when().get("/api/v1/kaufauftraege/" + UUID.randomUUID())
                .then().statusCode(404);
    }
}
