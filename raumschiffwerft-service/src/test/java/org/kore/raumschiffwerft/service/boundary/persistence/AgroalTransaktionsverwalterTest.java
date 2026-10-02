package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.service.control.DatenbankNichtErreichbar;
import org.kore.raumschiffwerft.service.control.SpansSammelndeTelemetrie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgroalTransaktionsverwalterTest {

    private final io.agroal.api.AgroalDataSource dataSource = mock(io.agroal.api.AgroalDataSource.class);
    private final Connection verbindung = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final SpansSammelndeTelemetrie telemetrie = new SpansSammelndeTelemetrie();
    private final Verbindungen verbindungen = new Verbindungen(dataSource, telemetrie);
    private final AgroalTransaktionsverwalter transaktionsverwalter =
            new AgroalTransaktionsverwalter(dataSource, verbindungen, telemetrie);

    @BeforeEach
    void verbindungBereitstellen() throws SQLException {
        when(dataSource.getConnection()).thenReturn(verbindung);
        when(verbindung.prepareStatement(anyString())).thenReturn(statement);
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
        // oeffnen() liefert den GetracedVerbindung-Wrapper der gemeinsamen
        // Verbindung: wiederholtes oeffnen liefert dieselbe Instanz, Delegation
        // geht an die rohe Verbindung, und schliessen() des Repos schliesst
        // die gemeinsame Verbindung NICHT - das tut nur der Verwalter
        transaktionsverwalter.inTransaktion(() -> {
            Connection erste = verbindungen.oeffnen();
            assertEquals(erste, verbindungen.oeffnen(), "zweites oeffnen liefert dieselbe Verbindung");
            erste.prepareStatement("SELECT x FROM auftrag");
            verbindungen.schliessen(erste);
            return null;
        });
        verify(verbindung).prepareStatement("SELECT x FROM auftrag");
        verify(verbindung).close();
    }

    @Test
    void eigeneVerbindungAusserhalbDerTransaktionWirdGeachtet() throws Exception {
        Connection eigene = verbindungen.oeffnen();
        eigene.prepareStatement("SELECT 1");

        // ausserhalb der Transaktion gehoert die Verbindung dem Aufrufer:
        // kein implizites Schliessen, erst schliessen() schliesst (delegiert)
        verify(verbindung, never()).close();
        verbindungen.schliessen(eigene);
        verify(verbindung).close();
    }

    @Test
    void commitFehlerMeldetIllegalStateUndRolltNichtNochmal() throws Exception {
        doThrow(new SQLException("commit gescheitert")).when(verbindung).commit();

        assertThrows(IllegalStateException.class,
                () -> transaktionsverwalter.inTransaktion(() -> "ok"));
        verify(verbindung).close();
    }

    @Test
    void transaktionOeffnetSpanMitCommitErgebnis() throws Exception {
        transaktionsverwalter.inTransaktion(() -> "ok");

        var span = telemetrie.span("db.transaktion");
        assertNotNull(span, "inTransaktion muss einen db.transaktion-Span oeffnen");
        assertEquals("commit", span.attribute().get("db.ergebnis"));
    }

    @Test
    void fehlschlagMarkiertTransaktionsSpanMitRollback() throws Exception {
        IllegalStateException fehler = new IllegalStateException("arbeit gescheitert");

        assertThrows(IllegalStateException.class,
                () -> transaktionsverwalter.inTransaktion(() -> {
                    throw fehler;
                }));

        var span = telemetrie.span("db.transaktion");
        assertNotNull(span);
        assertEquals("rollback", span.attribute().get("db.ergebnis"));
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.status());
        assertSame(fehler, span.ausnahmen().getFirst());
    }

    @Test
    void statementWaehrendDerTransaktionWirdGetract() throws Exception {
        when(statement.executeUpdate()).thenReturn(1);

        Integer ergebnis = transaktionsverwalter.inTransaktion(() -> {
            try (var ps = verbindungen.oeffnen().prepareStatement("INSERT INTO auftrag VALUES (?)")) {
                return ps.executeUpdate();
            }
        });

        assertEquals(1, ergebnis);
        assertNotNull(telemetrie.span("db.zugriff"), "Statement in Transaktion muss getract werden");
    }
}
