package org.kore.raumschiffwerft.adapter.imperium.boundary;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.adapter.imperium.control.ImperiumUebersetzer;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.AbfrageBestellstatusResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoerer;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoererResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.ImperiumWerft;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImperiumZielsystemTest {

    private final ImperiumWerft werft = mock(ImperiumWerft.class);
    private final SpansSammelndeTelemetrie telemetrie = new SpansSammelndeTelemetrie();
    @SuppressWarnings("unchecked")
    private final jakarta.enterprise.inject.Instance<io.opentelemetry.api.OpenTelemetry> openTelemetry =
            org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
    private ImperiumZielsystem zielsystem;

    @org.junit.jupiter.api.BeforeEach
    void telemetrieBereitstellen() {
        org.mockito.Mockito.when(openTelemetry.isUnsatisfied()).thenReturn(false);
        org.mockito.Mockito.when(openTelemetry.get()).thenReturn(telemetrie);
        zielsystem = new ImperiumZielsystem(werft, new ImperiumUebersetzer(), openTelemetry);
    }

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final Kaufauftrag auftrag = new Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_II, 2, 3);

    @Test
    void typIstImperium() {
        assertEquals(Zielsystemtyp.IMPERIUM, zielsystem.typ());
    }

    @Test
    void zustellungLiefertBestellnummer() throws ZustellungUngeklaert {
        BestelleSternenzerstoererResponse antwort = new BestelleSternenzerstoererResponse();
        antwort.setBestellnummer("ISD-4711");
        when(werft.bestelleSternenzerstoerer(any(BestelleSternenzerstoerer.class))).thenReturn(antwort);

        assertEquals("ISD-4711", zielsystem.zustellen(auftragsId, auftrag).externeReferenz());

        var span = telemetrie.span("adapter.imperium.zustellen");
        assertNotNull(span);
        assertEquals(span.attribute().get("durchlauferhitzer.zielsystem"), "IMPERIUM");
        assertEquals(span.attribute().get("durchlauferhitzer.auftrag.id"), auftragsId.wert().toString());
    }

    @Test
    void soapFaultWirdZuZustellungUngeklaert() throws ZustellungUngeklaert {
        RuntimeException fehler = new RuntimeException("SOAP-Fault");
        when(werft.bestelleSternenzerstoerer(any(BestelleSternenzerstoerer.class)))
                .thenThrow(fehler);

        assertThrows(ZustellungUngeklaert.class, () -> zielsystem.zustellen(auftragsId, auftrag));
        verify(werft, times(1)).bestelleSternenzerstoerer(any());

        var span = telemetrie.span("adapter.imperium.zustellen");
        assertNotNull(span);
        assertEquals(span.status(), io.opentelemetry.api.trace.StatusCode.ERROR);
        assertEquals(span.ausnahmen().getFirst(), fehler);
    }

    @Test
    void statusabfrageLiefertKanonischenStatus() throws ZustellungUngeklaert {
        AbfrageBestellstatusResponse antwort = new AbfrageBestellstatusResponse();
        antwort.setStatus("IN_BEARBEITUNG");
        antwort.setBestellnummer("ISD-4711");
        when(werft.abfrageBestellstatus(any())).thenReturn(antwort);

        Verarbeitungsstatus status = zielsystem.statusAbfragen(auftragsId);

        assertEquals(Verarbeitungsstatus.Status.IN_BEARBEITUNG, status.status());
        assertEquals("ISD-4711", status.externeReferenz());
        assertNotNull(telemetrie.span("adapter.imperium.statusAbfragen"));
    }

    @Test
    void fehlerBeiDerStatusabfrageWirdZuZustellungUngeklaert() {
        when(werft.abfrageBestellstatus(any())).thenThrow(new RuntimeException("Timeout"));

        assertThrows(ZustellungUngeklaert.class, () -> zielsystem.statusAbfragen(auftragsId));
        verify(werft, times(1)).abfrageBestellstatus(any());

        var span = telemetrie.span("adapter.imperium.statusAbfragen");
        assertNotNull(span);
        assertEquals(span.status(), io.opentelemetry.api.trace.StatusCode.ERROR);
    }
}
