package org.kore.raumschiffwerft.service.control;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.service.entity.Kaufauftrag;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnnahmeControllerTest {

    private final Transaktionsverwalter transaktionsverwalter = mock(Transaktionsverwalter.class);
    private final AuftragRepository auftragRepository = mock(AuftragRepository.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final ZustellungRepository zustellungRepository = mock(ZustellungRepository.class);
    private final Zielsystemwahl zielsystemwahl = mock(Zielsystemwahl.class);
    private final AnnahmeController controller = new AnnahmeController(transaktionsverwalter,
            auftragRepository, outboxRepository, zustellungRepository, zielsystemwahl,
            new TraceKontext(OpenTelemetry.noop()), new ObjectMapper(), 10, "test-pod");

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final org.kore.raumschiffwerft.model.entity.Kaufauftrag kanonisch =
            new org.kore.raumschiffwerft.model.entity.Kaufauftrag("Tarkin",
                    Sternenzerstoererklasse.IMPERIAL_I, 2, 7);

    @BeforeEach
    void transaktionDirektAusfuehren() throws Exception {
        when(transaktionsverwalter.inTransaktion(any())).thenAnswer(aufruf ->
                ((Transaktionsverwalter.Arbeit<?>) aufruf.getArgument(0)).ausfuehren());
        when(zielsystemwahl.waehlen(any(), any())).thenReturn(Zielsystemtyp.IMPERIUM);
    }

    @Test
    void neuannahmeSchreibtAuftragOutboxUndZustellung() {
        when(auftragRepository.anlegen(eq(auftragsId), anyString(), anyInt(), any(), any()))
                .thenReturn(1);

        AnnahmeErgebnis ergebnis = controller.annehmen(auftragsId, kanonisch, "{}", null);

        assertTrue(ergebnis.neu());
        verify(zielsystemwahl, org.mockito.Mockito.times(1)).waehlen(auftragsId, kanonisch);
        verify(auftragRepository).anlegen(eq(auftragsId), anyString(), anyInt(), any(), any());
        verify(outboxRepository).journalEinfuegen(eq(auftragsId), anyString(), any());
        verify(zustellungRepository).anlegen(any());
        assertEquals(1, ergebnis.auftrag().zustellungen().size());
        assertEquals(Zustellungsstatus.IN_ZUSTELLUNG, ergebnis.auftrag().zustellungen().get(0).status());
        assertEquals(Zielsystemtyp.IMPERIUM, ergebnis.auftrag().zustellungen().get(0).zielsystem());
    }

    @Test
    void konfliktLiefertBestehendenStandUndSchreibtNichtsWeiter() {
        when(auftragRepository.anlegen(eq(auftragsId), anyString(), anyInt(), any(), any()))
                .thenReturn(0);
        Kaufauftrag bestehend = new Kaufauftrag(auftragsId, kanonisch, java.time.OffsetDateTime.now(),
                List.of());
        when(auftragRepository.stand(auftragsId)).thenReturn(Optional.of(bestehend));

        AnnahmeErgebnis ergebnis = controller.annehmen(auftragsId, kanonisch, "{}", null);

        assertEquals(false, ergebnis.neu());
        assertEquals(bestehend, ergebnis.auftrag());
        verifyNoInteractions(outboxRepository, zustellungRepository);
    }

    @Test
    void datenbankNichtErreichbarWirdAlsSolchesGemeldet() {
        doThrow(new DatenbankNichtErreichbar(new IllegalStateException("connection refused")))
                .when(transaktionsverwalter).inTransaktion(any());

        assertThrows(DatenbankNichtErreichbar.class,
                () -> controller.annehmen(auftragsId, kanonisch, "{}", null));
        verify(auftragRepository, never()).anlegen(any(), any(), anyInt(), any(), any());
        verifyNoInteractions(outboxRepository, zustellungRepository);
    }

    @Test
    void fehlerWaehrendDerTransaktionMachtNichtsKaputtAusserDemDurchlauf() {
        when(auftragRepository.anlegen(eq(auftragsId), anyString(), anyInt(), any(), any()))
                .thenReturn(1);
        doThrow(new IllegalStateException("insert fehlgeschlagen"))
                .when(zustellungRepository).anlegen(any());

        assertThrows(IllegalStateException.class,
                () -> controller.annehmen(auftragsId, kanonisch, "{}", null));
    }
}
