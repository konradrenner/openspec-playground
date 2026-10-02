package org.kore.raumschiffwerft.adapter.rebellion.boundary;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.adapter.rebellion.control.RebellionUebersetzer;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAnfrage;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAntwort;
import org.kore.raumschiffwerft.adapter.rebellion.entity.StatusAntwort;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RebellionZielsystemTest {

    private final RebellionBeschaffungClient client = mock(RebellionBeschaffungClient.class);
    private final SpansSammelndeTelemetrie telemetrie = new SpansSammelndeTelemetrie();
    @SuppressWarnings("unchecked")
    private final jakarta.enterprise.inject.Instance<io.opentelemetry.api.OpenTelemetry> openTelemetry =
            org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
    private RebellionZielsystem zielsystem;

    @org.junit.jupiter.api.BeforeEach
    void telemetrieBereitstellen() {
        org.mockito.Mockito.when(openTelemetry.isUnsatisfied()).thenReturn(false);
        org.mockito.Mockito.when(openTelemetry.get()).thenReturn(telemetrie);
        zielsystem = new RebellionZielsystem(client, new RebellionUebersetzer(), openTelemetry);
    }

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final Kaufauftrag auftrag = new Kaufauftrag("Leia Organa", Sternenzerstoererklasse.VICTORY, 2, 5);

    @Test
    void typIstRebellion() {
        assertEquals(Zielsystemtyp.REBELLION, zielsystem.typ());
    }

    @Test
    void zustellungLiefertBeschaffungsId() throws ZustellungUngeklaert {
        when(client.beschaffungAnlegen(any(BeschaffungsAnfrage.class)))
                .thenReturn(new BeschaffungsAntwort("RB-1138", "IN_ARBEIT"));

        assertEquals("RB-1138", zielsystem.zustellen(auftragsId, auftrag).externeReferenz());

        var span = telemetrie.span("adapter.rebellion.zustellen");
        assertNotNull(span);
        assertEquals(span.attribute().get("durchlauferhitzer.zielsystem"), "REBELLION");
        assertEquals(span.attribute().get("durchlauferhitzer.auftrag.id"), auftragsId.wert().toString());
    }

    @Test
    void httpFehlerWirdZuZustellungUngeklaert() {
        when(client.beschaffungAnlegen(any(BeschaffungsAnfrage.class)))
                .thenThrow(new jakarta.ws.rs.InternalServerErrorException("500"));

        assertThrows(ZustellungUngeklaert.class, () -> zielsystem.zustellen(auftragsId, auftrag));
        verify(client, times(1)).beschaffungAnlegen(any());

        var span = telemetrie.span("adapter.rebellion.zustellen");
        assertNotNull(span);
        assertEquals(span.status(), io.opentelemetry.api.trace.StatusCode.ERROR);
    }

    @Test
    void statusabfrageLiefertKanonischenStatus() throws ZustellungUngeklaert {
        when(client.statusAbfragen(anyString())).thenReturn(new StatusAntwort("IN_ARBEIT"));

        assertEquals(Verarbeitungsstatus.Status.IN_BEARBEITUNG, zielsystem.statusAbfragen(auftragsId).status());

        when(client.statusAbfragen(anyString())).thenReturn(new StatusAntwort("ERLEDIGT"));

        assertEquals(Verarbeitungsstatus.Status.ABGESCHLOSSEN, zielsystem.statusAbfragen(auftragsId).status());

        when(client.statusAbfragen(anyString())).thenReturn(new StatusAntwort("UNBEKANNT"));

        assertEquals(Verarbeitungsstatus.Status.UNBEKANNT, zielsystem.statusAbfragen(auftragsId).status());
    }

    @Test
    void status404WirdAlsUnbekanntInterpretiert() throws ZustellungUngeklaert {
        when(client.statusAbfragen(anyString())).thenThrow(new jakarta.ws.rs.NotFoundException("404"));

        assertEquals(Verarbeitungsstatus.Status.UNBEKANNT, zielsystem.statusAbfragen(auftragsId).status());
        // 404 ist laut API-Vertrag kein Fehler: der Span bleibt unmarkiert
        var span = telemetrie.span("adapter.rebellion.statusAbfragen");
        assertNotNull(span);
        assertEquals(io.opentelemetry.api.trace.StatusCode.UNSET, span.status());
    }

    @Test
    void gewickelteStatus404WirdAlsUnbekanntInterpretiert() throws ZustellungUngeklaert {
        jakarta.ws.rs.WebApplicationException nichtGefunden = new jakarta.ws.rs.WebApplicationException("Not Found",
                jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.NOT_FOUND).build());
        when(client.statusAbfragen(anyString())).thenThrow(new RuntimeException("gewickelt", nichtGefunden));

        assertEquals(Verarbeitungsstatus.Status.UNBEKANNT, zielsystem.statusAbfragen(auftragsId).status());
    }

    @Test
    void timeoutBeiDerStatusabfrageWirdZuZustellungUngeklaert() {
        when(client.statusAbfragen(anyString()))
                .thenThrow(new jakarta.ws.rs.ProcessingException(new java.net.SocketTimeoutException()));

        assertThrows(ZustellungUngeklaert.class, () -> zielsystem.statusAbfragen(auftragsId));
        verify(client, times(1)).statusAbfragen(anyString());
    }
}
