package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.service.control.SpansSammelndeTelemetrie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests des Connection-Wrappers: jede Statement-Ausfuehrung oeffnet
 * einen db.zugriff-Span mit SQL, Operation und System; Fehler markieren den
 * Span; alle anderen Methoden werden transparent delegiert.
 */
class GetracedVerbindungTest {

    private final SpansSammelndeTelemetrie telemetrie = new SpansSammelndeTelemetrie();
    private final Connection roh = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final java.sql.ResultSet ergebnis = mock(java.sql.ResultSet.class);

    private final Connection gewrappt =
            GetracedVerbindung.verbinden(roh, telemetrie.getTracer("test"));

    @BeforeEach
    void statementBereitstellen() throws SQLException {
        when(roh.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(ergebnis);
    }

    @Test
    void executeQueryOeffnetSpanMitSqlOperationUndSystem() throws Exception {
        try (var rs = gewrappt.prepareStatement("SELECT x FROM auftrag").executeQuery()) {
            assertEquals(ergebnis, rs);
        }

        var span = telemetrie.span("db.zugriff");
        assertNotNull(span, "executeQuery muss einen db.zugriff-Span oeffnen");
        assertEquals("SELECT x FROM auftrag", span.attribute().get("db.sql"));
        assertEquals("select", span.attribute().get("db.operation"));
        assertEquals("postgresql", span.attribute().get("db.system"));
    }

    @Test
    void executeUpdateOeffnetSpanUndLeitetErgebnisWeiter() throws Exception {
        when(statement.executeUpdate()).thenReturn(3);

        assertEquals(3, gewrappt.prepareStatement("UPDATE auftrag SET x = ?").executeUpdate());

        assertNotNull(telemetrie.span("db.zugriff"));
    }

    @Test
    void sqlFehlerMarkiertSpanMitStatusFehler() throws Exception {
        when(statement.executeUpdate()).thenThrow(new SQLException("kaputt"));

        assertThrows(SQLException.class,
                () -> gewrappt.prepareStatement("DELETE FROM auftrag").executeUpdate());

        var span = telemetrie.span("db.zugriff");
        assertNotNull(span);
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.status());
        assertEquals(1, span.ausnahmen().size());
    }

    @Test
    void andereMethodenWerdenTransparentDelegiert() throws Exception {
        try (PreparedStatement ps = gewrappt.prepareStatement("SELECT x FROM auftrag")) {
            ps.setString(1, "wert");
        }

        verify(statement).setString(1, "wert");
        assertTrue(telemetrie.spans().isEmpty(), "ohne execute* darf kein Span entstehen");
    }

    @Test
    void operationIstDasErsteSqlSchluesselwort() throws Exception {
        when(statement.executeUpdate()).thenReturn(1);

        gewrappt.prepareStatement("UPDATE zustellung SET status = ?").executeUpdate();

        assertEquals("update", telemetrie.span("db.zugriff").attribute().get("db.operation"));
    }

    @Test
    void schliessenDelegiertAnDieRoheVerbindung() throws Exception {
        gewrappt.close();

        verify(roh).close();
        assertTrue(telemetrie.spans().isEmpty(), "close darf keinen Span oeffnen");
    }
}
