package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.service.control.DatenbankNichtErreichbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgroalTransaktionsverwalterTest {

    private final io.agroal.api.AgroalDataSource dataSource = mock(io.agroal.api.AgroalDataSource.class);
    private final Connection verbindung = mock(Connection.class);
    private final Verbindungen verbindungen = new Verbindungen(dataSource);
    private final AgroalTransaktionsverwalter transaktionsverwalter =
            new AgroalTransaktionsverwalter(dataSource, verbindungen);

    @BeforeEach
    void verbindungBereitstellen() throws SQLException {
        when(dataSource.getConnection()).thenReturn(verbindung);
    }

    @Test
    void erfolgCommittetUndSchliesst() throws Exception {
        String ergebnis = transaktionsverwalter.inTransaktion(() -> "ok");

        assertEquals("ok", ergebnis);
        verify(verbindung).setAutoCommit(false);
        verify(verbindung).commit();
        verify(verbindung, never()).rollback();
        verify(verbindung).close();
    }

    @Test
    void fehlerDerArbeitRolltZurueckUndMeldetIllegalState() throws Exception {
        IllegalStateException fehler = new IllegalStateException("arbeit gescheitert");

        IllegalStateException gemeldet = assertThrows(IllegalStateException.class,
                () -> transaktionsverwalter.inTransaktion(() -> {
                    throw fehler;
                }));

        assertSame(fehler, gemeldet.getCause());
        verify(verbindung).rollback();
        verify(verbindung, never()).commit();
    }

    @Test
    void datenbankNichtErreichbarWirdAlsSolcheGemeldet() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        assertThrows(DatenbankNichtErreichbar.class,
                () -> transaktionsverwalter.inTransaktion(() -> "ok"));
    }

    @Test
    void verbindungWaehrendDerTransaktionIstDieGemeinsame() throws Exception {
        Connection gesehen = transaktionsverwalter.inTransaktion(() -> verbindungen.oeffnen());

        assertSame(verbindung, gesehen);
        // die gemeinsame Verbindung wird vom Verwalter geschlossen, nicht vom Repository
        verify(verbindung).close();
    }

    @Test
    void eigeneVerbindungAusserhalbDerTransaktionWirdGeachtet() throws Exception {
        Connection eigene = verbindungen.oeffnen();

        assertSame(verbindung, eigene);
        // ausserhalb der Transaktion gehoert die Verbindung dem Aufrufer: kein implizites Schliessen
        verify(verbindung, org.mockito.Mockito.never()).close();
    }

    @Test
    void commitFehlerMeldetIllegalStateUndRolltNichtNochmal() throws Exception {
        doThrow(new SQLException("commit gescheitert")).when(verbindung).commit();

        assertThrows(IllegalStateException.class,
                () -> transaktionsverwalter.inTransaktion(() -> "ok"));
        verify(verbindung).close();
    }
}
