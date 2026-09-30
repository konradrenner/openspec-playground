package org.kore.raumschiffwerft.service.boundary;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import static io.restassured.RestAssured.given;

/**
 * Datenbank nicht erreichbar: Annahme antwortet mit 503 und
 * Retry-After, ohne dass etwas geschrieben wurde.
 */
@QuarkusTest
@TestProfile(DatenbankWegProfile.class)
class AnnahmeDatenbankWegIT {

    @Test
    void datenbankNichtErreichbarLiefert503MitRetryAfter() {
        given()
                .header("Idempotency-Key", UUID.randomUUID())
                .header("Content-Type", "application/json")
                .body("{\"kaeufer\": \"Tarkin\", \"klasse\": \"IMPERIAL_I\", \"anzahl\": 2, \"lieferplanet\": 42}")
                .when().post("/api/v1/kaufauftraege")
                .then()
                .statusCode(503)
                .header("Retry-After", "5");
    }
}
