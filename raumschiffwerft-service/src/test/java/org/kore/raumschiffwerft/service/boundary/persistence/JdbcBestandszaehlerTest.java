package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.agroal.api.AgroalDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcBestandszaehlerTest {

    private final AgroalDataSource dataSource = Mockito.mock(AgroalDataSource.class);
    private final Connection verbindung = Mockito.mock(Connection.class);
    private final PreparedStatement statement = Mockito.mock(PreparedStatement.class);
    private final ResultSet resultSet = Mockito.mock(ResultSet.class);

    private JdbcBestandszaehler zaehler;

    @BeforeEach
    void aufbauen() throws SQLException {
        Mockito.when(dataSource.getConnection()).thenReturn(verbindung);
        Mockito.when(verbindung.prepareStatement(Mockito.anyString())).thenReturn(statement);
        Mockito.when(statement.executeQuery()).thenReturn(resultSet);
        Mockito.when(resultSet.next()).thenReturn(true);
        zaehler = new JdbcBestandszaehler(dataSource);
    }

    @Test
    void zaehlungLiestDenCountAusDerDatenbank() throws SQLException {
        Mockito.when(resultSet.getLong(1)).thenReturn(5L);

        assertEquals(5, zaehler.offeneZustellungen());
        assertEquals(5, zaehler.fehlgeschlageneZustellungen());
        assertEquals(5, zaehler.outboxRueckstand());
    }

    @Test
    void zaehlfehlerLiefertNull() throws SQLException {
        Mockito.when(dataSource.getConnection()).thenThrow(new SQLException("weg"));

        assertEquals(0, zaehler.offeneZustellungen());
        assertEquals(0, zaehler.fehlgeschlageneZustellungen());
        assertEquals(0, zaehler.outboxRueckstand());
    }
}
