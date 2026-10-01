package org.kore.raumschiffwerft.service.control;

import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BetriebsmetrikenTest {

    private final Bestandszaehler bestandszaehler = mock(Bestandszaehler.class);
    private final MessendeTelemetrie telemetrie = new MessendeTelemetrie();
    private final Betriebsmetriken metriken = new Betriebsmetriken(telemetrie, bestandszaehler);

    @Test
    void gaugesWerdenRegistriertUndMessenDieBestaende() {
        when(bestandszaehler.offeneZustellungen()).thenReturn(5L);
        when(bestandszaehler.fehlgeschlageneZustellungen()).thenReturn(2L);
        when(bestandszaehler.outboxRueckstand()).thenReturn(3L);

        metriken.gaugesRegistrieren(null);
        telemetrie.gaugesMessen();

        assertEquals(5, telemetrie.gaugeWert("durchlauferhifter.zustellungen.offen"));
        assertEquals(2, telemetrie.gaugeWert("durchlauferhifter.zustellungen.fehlgeschlagen"));
        assertEquals(3, telemetrie.gaugeWert("durchlauferhifter.outbox.rueckstand"));
    }

    @Test
    void gaugesMessenErneutBeiJederAbfrage() {
        when(bestandszaehler.offeneZustellungen()).thenReturn(1L).thenReturn(7L);

        metriken.gaugesRegistrieren(null);

        telemetrie.gaugesMessen();
        assertEquals(1, telemetrie.gaugeWert("durchlauferhifter.zustellungen.offen"));

        telemetrie.gaugesMessen();
        assertEquals(7, telemetrie.gaugeWert("durchlauferhifter.zustellungen.offen"));
    }
}
