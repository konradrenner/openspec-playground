package org.kore.raumschiffwerft.service.control.journal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.agroal.api.AgroalDataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalRelayTest {

    private final AgroalDataSource dataSource = mock(AgroalDataSource.class);
    private final Connection verbindung = mock(Connection.class);
    private final JournalOutboxRepository outboxRepository = mock(JournalOutboxRepository.class);
    private final JournalVersand versand = mock(JournalVersand.class);

    private final JournalRelay relay =
            new JournalRelay(dataSource, outboxRepository, versand, 50);

    private final Journaleintrag erster =
            new Journaleintrag(1, "11111111-1111-1111-1111-111111111111", "{\"auftragsId\": \"1\"}");
    private final Journaleintrag zweiter =
            new Journaleintrag(2, "22222222-2222-2222-2222-222222222222", "{\"auftragsId\": \"2\"}");

    @BeforeEach
    void aufbauen() throws SQLException {
        when(dataSource.getConnection()).thenReturn(verbindung);
    }

    @Test
    void versendetJedenEintragUndMarkiertErstDanach() throws Exception {
        when(outboxRepository.ungesendeteLesen(verbindung, 50)).thenReturn(List.of(erster, zweiter));

        relay.relay();

        InOrder reihenfolge = inOrder(versand, outboxRepository, verbindung);
        reihenfolge.verify(versand).senden(erster.auftragsId(), erster.payload());
        reihenfolge.verify(outboxRepository).gesendetMarkieren(any(), anyLong(), any());
        reihenfolge.verify(versand).senden(zweiter.auftragsId(), zweiter.payload());
        reihenfolge.verify(outboxRepository).gesendetMarkieren(any(), anyLong(), any());
        reihenfolge.verify(verbindung).commit();
        reihenfolge.verify(verbindung, never()).rollback();
    }

    @Test
    void sendefehlerRolltZurueckOhneCommit() throws Exception {
        when(outboxRepository.ungesendeteLesen(verbindung, 50)).thenReturn(List.of(erster));
        doThrow(new IllegalStateException("Kafka weg")).when(versand).senden(anyString(), anyString());

        assertDoesNotThrow(() -> relay.relay());

        verify(verbindung).rollback();
        verify(verbindung, never()).commit();
        verify(outboxRepository, never()).gesendetMarkieren(any(), anyLong(), any());
    }

    @Test
    void leereOutboxCommittetOhneVersand() throws Exception {
        when(outboxRepository.ungesendeteLesen(verbindung, 50)).thenReturn(List.of());

        relay.relay();

        verify(versand, never()).senden(anyString(), anyString());
        verify(verbindung).commit();
    }

    @Test
    void datenbankfehlerWirftNicht() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Datenbank weg"));

        assertDoesNotThrow(() -> relay.relay());

        verify(versand, never()).senden(anyString(), anyString());
    }
}
