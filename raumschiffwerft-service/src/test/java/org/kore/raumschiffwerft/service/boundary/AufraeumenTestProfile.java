package org.kore.raumschiffwerft.service.boundary;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Testprofil des Aufraeumens: Aufbewahrung 1 s und Timer 500 ms, damit
 * die IT schnell konvergiert. Bewusst NICHT im globalen Test-Profil,
 * damit kein anderer IT Zeilen verliert, bevor er sie prueft.
 */
public class AufraeumenTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "durchlauferhitzer.aufraeumen.aufbewahrung", "1s",
                "durchlauferhitzer.aufraeumen.timer-period-millis", "500");
    }
}
