package org.kore.raumschiffwerft.adapter.imperium.boundary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ImperiumAdapterIT {

    private static final HttpClient ADMIN = HttpClient.newHttpClient();
    private static final URI SZENARIEN_ZURUECKSETZEN = URI.create("http://localhost:8089/__admin/scenarios/reset");

    @Inject
    ImperiumZielsystem zielsystem;

    @BeforeAll
    static void szenarienZuruecksetzen() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(SZENARIEN_ZURUECKSETZEN)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        ADMIN.send(request, HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void typIstImperium() {
        assertEquals(Zielsystemtyp.IMPERIUM, zielsystem.typ());
    }

    @Test
    void bestellungErfolgreich() throws ZustellungUngeklaert {
        Kaufauftrag auftrag = new Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_I, 2, 7);

        assertEquals("ISD-4711", zielsystem.zustellen(new AuftragsId(UUID.randomUUID()), auftrag).externeReferenz());
    }

    @Test
    void bestellungJarJarWirftZustellungUngeklaert() {
        Kaufauftrag auftrag = new Kaufauftrag("Jar Jar Binks", Sternenzerstoererklasse.VICTORY, 1, 1);

        assertThrows(ZustellungUngeklaert.class,
                () -> zielsystem.zustellen(new AuftragsId(UUID.randomUUID()), auftrag));
    }

    @Test
    void bestellungLangsamBrichtNach2SekundenAb() {
        Kaufauftrag auftrag = new Kaufauftrag("Langsam Kaufmann", Sternenzerstoererklasse.EXECUTOR, 1, 1);

        long start = System.nanoTime();
        assertThrows(ZustellungUngeklaert.class,
                () -> zielsystem.zustellen(new AuftragsId(UUID.randomUUID()), auftrag));
        Duration vergangen = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(vergangen.toMillis() < 5000,
                "Der Aufruf haette nach 2 s abbrechen muessen, dauerte aber %s".formatted(vergangen));
    }

    @Test
    void statusabfrageSzenarioErstInBearbeitungDannAbgeschlossen() throws ZustellungUngeklaert {
        AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());

        Verarbeitungsstatus erster = zielsystem.statusAbfragen(auftragsId);
        assertEquals(Verarbeitungsstatus.Status.IN_BEARBEITUNG, erster.status());
        assertEquals("ISD-4711", erster.externeReferenz());

        Verarbeitungsstatus zweiter = zielsystem.statusAbfragen(auftragsId);
        assertEquals(Verarbeitungsstatus.Status.ABGESCHLOSSEN, zweiter.status());
        assertEquals("ISD-4711", zweiter.externeReferenz());
    }
}
