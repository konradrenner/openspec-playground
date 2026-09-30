package org.kore.raumschiffwerft.service.control;

import java.sql.Connection;
import java.sql.SQLException;
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
import io.agroal.api.AgroalDataSource;
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

    private final AgroalDataSource dataSource = mock(AgroalDataSource.class);
    private final Connection verbindung = mock(Connection.class);
    private final AuftragRepository auftragRepository = mock(AuftragRepository.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final ZustellungRepository zustellungRepository = mock(ZustellungRepository.class);
    private final Zielsystemwahl zielsystemwahl = mock(Zielsystemwahl.class);
    private final AnnahmeController controller = new AnnahmeController(dataSource, auftragRepository,
            outboxRepository, zustellungRepository, zielsystemwahl,
            new TraceKontext(OpenTelemetry.noop()), new ObjectMapper(), 10, "test-pod");

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final org.kore.raumschiffwerft.model.entity.Kaufauftrag kanonisch =
            new org.kore.raumschiffwerft.model.entity.Kaufauftrag("Tarkin",
                    Sternenzerstoererklasse.IMPERIAL_I, 2, 7);

    @BeforeEach
    void verbindungBereitstellen() throws SQLException {
        when(dataSource.getConnection()).thenReturn(verbindung);
        when(zielsystemwahl.waehlen(any(), any())).thenReturn(Zielsystemtyp.IMPERIUM);
    }

    @Test
    void neuannahmeSchreibtAuftragOutboxUndZustellungAtomar() throws SQLException {
        when(auftragRepository.anlegen(eq(verbindung), eq(auftragsId), anyString(), anyInt(), any(), any()))
                .thenReturn(1);

        AnnahmeErgebnis ergebnis = controller.annehmen(auftragsId, kanonisch, "{}", null);

        assertTrue(ergebnis.neu());
        verify(zielsystemwahl, org.mockito.Mockito.times(1)).waehlen(auftragsId, kanonisch);
        verify(auftragRepository).anlegen(eq(verbindung), eq(auftragsId), anyString(), anyInt(), any(), any());
        verify(outboxRepository).journalEinfuegen(eq(verbindung), eq(auftragsId), anyString(), any());
        verify(zustellungRepository).anlegen(eq(verbindung), any());
        verify(verbindung).commit();
        verify(verbindung, never()).rollback();
        assertEquals(1, ergebnis.auftrag().zustellungen().size());
        assertEquals(Zustellungsstatus.IN_ZUSTELLUNG, ergebnis.auftrag().zustellungen().get(0).status());
        assertEquals(Zielsystemtyp.IMPERIUM, ergebnis.auftrag().zustellungen().get(0).zielsystem());
    }

    @Test
    void konfliktLiefertBestehendenStandUndSchreibtNichtsWeiter() throws SQLException {
        when(auftragRepository.anlegen(eq(verbindung), eq(auftragsId), anyString(), anyInt(), any(), any()))
                .thenReturn(0);
        Kaufauftrag bestehend = new Kaufauftrag(auftragsId, kanonisch, java.time.OffsetDateTime.now(),
                List.of());
        when(auftragRepository.stand(auftragsId)).thenReturn(Optional.of(bestehend));

        AnnahmeErgebnis ergebnis = controller.annehmen(auftragsId, kanonisch, "{}", null);

        assertEquals(false, ergebnis.neu());
        assertEquals(bestehend, ergebnis.auftrag());
        verify(verbindung).rollback();
        verifyNoInteractions(outboxRepository, zustellungRepository);
        verify(verbindung, never()).commit();
    }

    @Test
    void datenbankNichtErreichbarWirdAlsSolchesGemeldet() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        assertThrows(DatenbankNichtErreichbar.class,
                () -> controller.annehmen(auftragsId, kanonisch, "{}", null));
        verifyNoInteractions(outboxRepository, zustellungRepository);
    }

    @Test
    void fehlerWaehrendDerTransaktionMachtRollbackUndSchreibtNichts() throws SQLException {
        when(auftragRepository.anlegen(eq(verbindung), eq(auftragsId), anyString(), anyInt(), any(), any()))
                .thenReturn(1);
        doThrow(new SQLException("insert fehlgeschlagen"))
                .when(zustellungRepository).anlegen(any(), any());

        assertThrows(IllegalStateException.class,
                () -> controller.annehmen(auftragsId, kanonisch, "{}", null));
        verify(verbindung).rollback();
        verify(verbindung, never()).commit();
    }
}
