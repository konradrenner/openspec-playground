package org.kore.raumschiffwerft.service.control.aufraeumen;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AufbewahrungsregelTest {

    private final OffsetDateTime jetzt = OffsetDateTime.parse("2026-09-30T12:00:00Z");
    private final Aufbewahrungsregel regel = new Aufbewahrungsregel(Duration.ofDays(7));

    @Test
    void stichtagIstJetztMinusAufbewahrung() {
        assertEquals(jetzt.minusDays(7), regel.stichtag(jetzt));
    }

    @Test
    void abgeschlossenerUndAbgelaufenerAuftragIstLoeschbar() {
        assertTrue(regel.loeschbar(List.of(Zustellungsstatus.BESTAETIGT),
                jetzt.minusDays(8), jetzt));
        assertTrue(regel.loeschbar(
                List.of(Zustellungsstatus.BESTAETIGT, Zustellungsstatus.BESTAETIGT),
                jetzt.minusDays(30), jetzt));
    }

    @Test
    void frischerAuftragBleibt() {
        assertFalse(regel.loeschbar(List.of(Zustellungsstatus.BESTAETIGT),
                jetzt.minusSeconds(1), jetzt));
        assertFalse(regel.loeschbar(List.of(Zustellungsstatus.BESTAETIGT), jetzt, jetzt));
    }

    @Test
    void fehlgeschlagenerAuftragWirdNieGeloescht() {
        assertFalse(regel.loeschbar(
                List.of(Zustellungsstatus.BESTAETIGT, Zustellungsstatus.FEHLGESCHLAGEN),
                jetzt.minusYears(10), jetzt));
    }

    @Test
    void offeneOderUngeklaerteZustellungenHaltenDenAuftrag() {
        for (Zustellungsstatus offen : List.of(Zustellungsstatus.IN_ZUSTELLUNG,
                Zustellungsstatus.IN_ABGLEICH, Zustellungsstatus.UNGEKLAERT)) {
            assertFalse(regel.loeschbar(List.of(Zustellungsstatus.BESTAETIGT, offen),
                    jetzt.minusDays(30), jetzt), "mit %s".formatted(offen));
        }
    }

    @Test
    void ohneZustellungenOderOhneAnnahmeIstNichtsLoeschbar() {
        assertFalse(regel.loeschbar(List.of(), jetzt.minusDays(30), jetzt));
        assertFalse(regel.loeschbar(List.of(Zustellungsstatus.BESTAETIGT), null, jetzt));
    }
}
