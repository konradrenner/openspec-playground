package org.kore.raumschiffwerft.service.boundary.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.kore.raumschiffwerft.service.control.DatenbankNichtErreichbar;
import org.kore.raumschiffwerft.service.control.Transaktionsverwalter;

/**
 * Fuehrt Arbeit des control in EINER Datenbank-Transaktion aus: Verbindung
 * oeffnen, setAutoCommit(false), Arbeit ausfuehren, committen - bei jedem
 * Fehler zurueckrollen und als IllegalStateException melden. Ist die
 * Datenbank nicht erreichbar, wird DatenbankNichtErreichbar gemeldet.
 * Die Verbindung wird waehrend der Transaktion ueber die Verbindungen den
 * JDBC-Implementierungen im selben Paket zur Verfuegung gestellt. Die ganze
 * Transaktion laeuft in einem db.transaktion-Span; das Ergebnis (commit
 * bzw. rollback) steht als Attribut db.ergebnis am Span, die rollende
 * Ausnahme zusaetzlich als Fehler-Markierung.
 */
@ApplicationScoped
public class AgroalTransaktionsverwalter implements Transaktionsverwalter {

    private static final String ATTRIBUTE_ERGEBNIS = "db.ergebnis";

    private final AgroalDataSource dataSource;
    private final Verbindungen verbindungen;
    private final Tracer tracer;

    @Inject
    public AgroalTransaktionsverwalter(AgroalDataSource dataSource, Verbindungen verbindungen,
                                       OpenTelemetry openTelemetry) {
        this.dataSource = dataSource;
        this.verbindungen = verbindungen;
        this.tracer = GetracedVerbindung.tracer(openTelemetry);
    }

    @Override
    public <T> T inTransaktion(Arbeit<T> arbeit) {
        Connection verbindung;
        try {
            verbindung = dataSource.getConnection();
        } catch (SQLException | RuntimeException e) {
            throw new DatenbankNichtErreichbar(e);
        }
        Span span = tracer.spanBuilder("db.transaktion")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        try (Scope ignoriert = span.makeCurrent()) {
            try (verbindung) {
                verbindung.setAutoCommit(false);
                verbindungen.setzen(verbindung);
                try {
                    T ergebnis = arbeit.ausfuehren();
                    verbindung.commit();
                    span.setAttribute(ATTRIBUTE_ERGEBNIS, "commit");
                    return ergebnis;
                } catch (Exception e) {
                    try {
                        verbindung.rollback();
                    } catch (SQLException rollbackFehler) {
                        e.addSuppressed(rollbackFehler);
                    }
                    span.setAttribute(ATTRIBUTE_ERGEBNIS, "rollback");
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR);
                    throw new IllegalStateException("Transaktion gescheitert", e);
                }
            } catch (SQLException e) {
                span.setStatus(StatusCode.ERROR);
                throw new IllegalStateException("Transaktion nicht durchfuehrbar", e);
            } finally {
                verbindungen.entfernen();
            }
        } finally {
            span.end();
        }
    }
}
