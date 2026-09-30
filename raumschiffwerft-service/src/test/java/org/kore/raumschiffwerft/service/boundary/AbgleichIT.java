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
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

/**
 * Integrationstest des Abgleichs gegen die devenv-Postgres und die
 * WireMock-Stubs: fehlgeschlagene und abgestuerzte Zustellungen werden
 * von der Timer-Route wieder aufgegriffen und konvergieren gegen
 * BESTAETIGT. Die WireMock-Szenarien werden vor jedem Test
 * zurueckgesetzt, damit der Ablauf (IN_BEARBEITUNG, dann ABGESCHLOSSEN)
 * deterministisch ist.
 */
@QuarkusTest
class AbgleichIT {

    private static final HttpClient ADMIN = HttpClient.newHttpClient();
    private static final URI SZENARIEN_ZURUECKSETZEN =
            URI.create("http://localhost:8089/__admin/scenarios/reset");

    /** Rebellion-AuftragsId, fuer die WireMock den Status UNBEKANNT (404) liefert. */
    private static final UUID UNBEKANNTE_BESCHAFFUNG =
            UUID.fromString("00000000-0000-0000-0000-000000000404");

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void szenarienZuruecksetzenUndTabelleAufraeumen() throws Exception {
        ADMIN.send(HttpRequest.newBuilder(SZENARIEN_ZURUECKSETZEN)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.discarding());
        // Zeilen aus frueheren Durchlaeufen entfernen, damit der Abgleich
        // nur die Zeilen dieses Tests sieht (Produktions-Zeilen werden
        // nie geloescht - das hier ist Testvorbereitung).
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

    /**
     * Legt auftrag- und zustellung-Zeile direkt per SQL an - zum Beispiel
     * fuer den simulierten Absturz vor dem externen Aufruf (abgelaufene
     * Lease in IN_ZUSTELLUNG) oder eine faellige UNGEKLAERT-Zeile.
     */
    private void offeneZeileAnlegen(UUID auftragsId, String zielsystem, String status, int versuche,
                                   OffsetDateTime naechsterVersuchUm, OffsetDateTime leaseBis,
                                   String kaufauftragJson) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement auftrag = c.prepareStatement(
                    "INSERT INTO auftrag (auftrags_id, kaufauftrag, schema_version, trace_id, angenommen_am) "
                            + "VALUES (?, ?::jsonb, 1, NULL, now())");
                 PreparedStatement zustellung = c.prepareStatement(
                         "INSERT INTO zustellung (auftrags_id, zielsystem, status, versuche, "
                                 + "naechster_versuch_um, lease_bis, instanz, aktualisiert_am) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, 'absturz-pod', now())")) {
                auftrag.setObject(1, auftragsId);
                auftrag.setString(2, kaufauftragJson);
                auftrag.executeUpdate();
                zustellung.setObject(1, auftragsId);
                zustellung.setString(2, zielsystem);
                zustellung.setString(3, status);
                zustellung.setInt(4, versuche);
                zustellung.setObject(5, naechsterVersuchUm);
                zustellung.setObject(6, leaseBis);
                zustellung.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Test
    void fehlerfallKonvergiertZuBestaetigt() {
        UUID id = UUID.randomUUID();

        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Jar Jar Binks", 42))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(202)
                .body("zustellungen[0].status", equalTo("UNGEKLAERT"));

        // Statusabfrage 1: IN_BEARBEITUNG -> UNGEKLAERT, Statusabfrage 2: ABGESCHLOSSEN
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> given()
                        .when().get("/api/v1/kaufauftraege/" + id)
                        .then()
                        .statusCode(200)
                        .body("zustellungen[0].status", equalTo("BESTAETIGT"))
                        .body("zustellungen[0].externeReferenz", equalTo("ISD-4711")));
    }

    @Test
    void timeoutfallKonvergiertZuBestaetigt() {
        UUID id = UUID.randomUUID();

        // der Langsam-Stub ueberschreitet das 2-s-Timeout des Imperium-Clients
        given()
                .header("Idempotency-Key", id)
                .header("Content-Type", "application/json")
                .body(body("Langsam Kaufmann", 42))
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(202)
                .body("zustellungen[0].status", equalTo("UNGEKLAERT"));

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> given()
                        .when().get("/api/v1/kaufauftraege/" + id)
                        .then()
                        .statusCode(200)
                        .body("zustellungen[0].status", equalTo("BESTAETIGT"))
                        .body("zustellungen[0].externeReferenz", equalTo("ISD-4711")));
    }

    @Test
    void abgelaufeneLeaseInInZustellungWirdAufgegriffen() throws SQLException {
        UUID id = UUID.randomUUID();
        // simulierter Absturz vor dem externen Aufruf: Lease abgelaufen
        offeneZeileAnlegen(id, "IMPERIUM", "IN_ZUSTELLUNG", 0, null,
                OffsetDateTime.now().minusSeconds(10),
                "{\"kaeufer\": \"Tarkin\", \"klasse\": \"IMPERIAL_I\", \"anzahl\": 2, \"lieferplanet\": 42}");

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> given()
                        .when().get("/api/v1/kaufauftraege/" + id)
                        .then()
                        .statusCode(200)
                        .body("zustellungen[0].status", equalTo("BESTAETIGT"))
                        .body("zustellungen[0].externeReferenz", equalTo("ISD-4711"))
                        // zwei Beanspruchungen: IN_BEARBEITUNG, dann ABGESCHLOSSEN
                        .body("zustellungen[0].versuche", equalTo(2)));

        // die Zeile wurde aufgegriffen, nicht neu angelegt
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("auftrag", id));
        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("zustellung", id));
    }

    @Test
    void unbekanntLoestNeuversandAus() throws SQLException {
        // Rebellion-Statusabfrage auf diese Id liefert 404 = UNBEKANNT
        offeneZeileAnlegen(UNBEKANNTE_BESCHAFFUNG, "REBELLION", "UNGEKLAERT", 1,
                OffsetDateTime.now().minusSeconds(10), null,
                "{\"kaeufer\": \"Mon Mothma\", \"klasse\": \"VICTORY\", \"anzahl\": 1, \"lieferplanet\": 4}");

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> given()
                        .when().get("/api/v1/kaufauftraege/" + UNBEKANNTE_BESCHAFFUNG)
                        .then()
                        .statusCode(200)
                        .body("zustellungen[0].status", equalTo("BESTAETIGT"))
                        .body("zustellungen[0].externeReferenz", equalTo("RB-1138"))
                        // mindestens ein Neuversand nach der Beanspruchung
                        .body("zustellungen[0].versuche", org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        org.junit.jupiter.api.Assertions.assertEquals(1, zeilen("zustellung", UNBEKANNTE_BESCHAFFUNG));
    }
}
