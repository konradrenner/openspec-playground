package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackoffTest {

    private final OffsetDateTime jetzt = OffsetDateTime.parse("2026-09-30T12:00:00Z");

    @Test
    void waechstExponentiellMitDenVersuchen() {
        Backoff backoff = new Backoff(60, 3600, 0.0, new Random(1));

        assertEquals(jetzt.plusSeconds(60), backoff.naechsterVersuchUm(jetzt, 1));
        assertEquals(jetzt.plusSeconds(120), backoff.naechsterVersuchUm(jetzt, 2));
        assertEquals(jetzt.plusSeconds(240), backoff.naechsterVersuchUm(jetzt, 3));
    }

    @Test
    void capDeckeltDenBackoff() {
        Backoff backoff = new Backoff(60, 3600, 0.0, new Random(1));

        assertEquals(jetzt.plusSeconds(3600), backoff.naechsterVersuchUm(jetzt, 7));
        assertEquals(jetzt.plusSeconds(3600), backoff.naechsterVersuchUm(jetzt, 20));
    }

    @Test
    void jitterBleibtInnerhalbVon20Prozent() {
        Backoff backoff = new Backoff(60, 3600, 0.2, new Random(42));

        for (int i = 0; i < 100; i++) {
            OffsetDateTime naechster = backoff.naechsterVersuchUm(jetzt, 1);
            long sekunden = ChronoUnit.SECONDS.between(jetzt, naechster);
            assertTrue(sekunden >= 60 && sekunden <= 72,
                    "Backoff %d s ausserhalb [60, 72]".formatted(sekunden));
        }
    }

    @Test
    void versucheUnterEinsWerdenWieEinsBehandelt() {
        Backoff backoff = new Backoff(60, 3600, 0.0, new Random(1));

        assertEquals(jetzt.plusSeconds(60), backoff.naechsterVersuchUm(jetzt, 0));
    }

    @Test
    void unzulaessigeWerteWerdenAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(-1, 3600, 0.2, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(60, -1, 0.2, new Random(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Backoff(60, 3600, 1.1, new Random(1)));
    }
}
