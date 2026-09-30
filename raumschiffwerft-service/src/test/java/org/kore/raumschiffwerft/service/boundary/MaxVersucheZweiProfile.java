package org.kore.raumschiffwerft.service.boundary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Testprofil fuer die Endgueltigkeits-IT: max-versuche 2, damit eine
 * durchgehend scheiternde Zustellung schnell FEHLGESCHLAGEN wird.
 */
public class MaxVersucheZweiProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("abgleich.max-versuche", "2");
    }
}
