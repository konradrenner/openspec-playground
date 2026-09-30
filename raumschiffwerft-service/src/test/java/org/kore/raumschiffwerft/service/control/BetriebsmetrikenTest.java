package org.kore.raumschiffwerft.service.control;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BetriebsmetrikenTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AgroalDataSource dataSource = Mockito.mock(AgroalDataSource.class);
    private final Connection verbindung = Mockito.mock(Connection.class);
    private final PreparedStatement statement = Mockito.mock(PreparedStatement.class);
    private final ResultSet resultSet = Mockito.mock(ResultSet.class);

    private Betriebsmetriken metriken;

    @BeforeEach
    void aufbauen() throws SQLException {
        Mockito.when(dataSource.getConnection()).thenReturn(verbindung);
        Mockito.when(verbindung.prepareStatement(Mockito.anyString())).thenReturn(statement);
        Mockito.when(statement.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true);
        metriken = new Betriebsmetriken(registry, dataSource);
    }

    @Test
    void gaugesWerdenRegistriert() {
        metriken.gaugesRegistrieren(null);

        assertNotNull(registry.get("durchlauferhitzer.zustellungen.offen").gauge());
        assertNotNull(registry.get("durchlauferhitzer.zustellungen.fehlgeschlagen").gauge());
        assertNotNull(registry.get("durchlauferhitzer.outbox.rueckstand").gauge());
    }

    @Test
    void zaehlungLiestDenCountAusDerDatenbank() throws SQLException {
        Mockito.when(resultSet.getLong(1)).thenReturn(5L);

        assertEquals(5, metriken.zaehlen("SELECT count(*) FROM zustellung WHERE status = 'X'"));
    }

    @Test
    void zaehlfehlerLiefertNull() throws SQLException {
        Mockito.when(dataSource.getConnection()).thenThrow(new SQLException("weg"));

        assertEquals(0, metriken.zaehlen("SELECT count(*)"));
    }
}
