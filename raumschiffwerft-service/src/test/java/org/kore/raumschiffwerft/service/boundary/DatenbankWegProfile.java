package org.kore.raumschiffwerft.service.boundary;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Testprofil mit unerreichbarem Datenbank-Endpunkt: die Anwendung startet
 * (Flyway deaktiviert), aber jede Annahme schlaegt bei der
 * Verbindungsbeschaffung fehl.
 */
public class DatenbankWegProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.jdbc.url", "jdbc:postgresql://localhost:59999/raumschiffwerft",
                "quarkus.flyway.migrate-at-start", "false",
                "quarkus.datasource.jdbc.acquisition-timeout", "1S");
    }
}
