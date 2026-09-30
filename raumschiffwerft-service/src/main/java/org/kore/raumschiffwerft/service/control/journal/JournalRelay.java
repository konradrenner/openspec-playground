package org.kore.raumschiffwerft.service.control.journal;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.agroal.api.AgroalDataSource;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Journal-Relay: versendet ungesendete Outbox-Zeilen in Batches
 * zuverlaessig nach Kafka (at-least-once). Der Durchlauf laeuft in einer
 * kurzen Transaktion: Zeilen mit FOR UPDATE SKIP LOCKED beanspruchen,
 * jede Zeile synchron senden (auf Bestaetigung warten), danach
 * gesendet_am setzen und committen. Ein Sendefehler rollt die
 * Transaktion zurueck - bereits gesendete Zeilen duerfen dann spaeter
 * erneut gesendet werden (Duplikate sind erlaubt). Ist Kafka nicht
 * erreichbar, bleibt die Outbox unberuehrt und der Rueckstand wird beim
 * naechsten Durchlauf nachgeholt; die Annahme ist davon unberuehrt.
 */
@ApplicationScoped
public class JournalRelay {

    private static final Logger LOG = Logger.getLogger(JournalRelay.class);

    private final AgroalDataSource dataSource;
    private final JournalOutboxRepository outboxRepository;
    private final JournalVersand versand;
    private final Tracer tracer;
    private final int batch;

    @Inject
    public JournalRelay(AgroalDataSource dataSource, JournalVersand versand,
                        OpenTelemetry openTelemetry,
                        @ConfigProperty(name = "journal.relay.batch") int batch) {
        this.dataSource = dataSource;
        this.outboxRepository = new JournalOutboxRepository();
        this.versand = versand;
        this.tracer = openTelemetry.getTracer("durchlauferhitzer.journal");
        this.batch = batch;
    }

    JournalRelay(AgroalDataSource dataSource, JournalOutboxRepository outboxRepository,
                 JournalVersand versand, OpenTelemetry openTelemetry, int batch) {
        this.dataSource = dataSource;
        this.outboxRepository = outboxRepository;
        this.versand = versand;
        this.tracer = openTelemetry.getTracer("durchlauferhitzer.journal");
        this.batch = batch;
    }

    /** Ein Relay-Durchlauf; Fehler werden geloggt, nicht geworfen. */
    public void relay() {
        try (Connection verbindung = dataSource.getConnection()) {
            verbindung.setAutoCommit(false);
            try {
                List<Journaleintrag> eintraege = outboxRepository.ungesendeteLesen(verbindung, batch);
                OffsetDateTime gesendetAm = OffsetDateTime.now();
                for (Journaleintrag eintrag : eintraege) {
                    versenden(verbindung, eintrag, gesendetAm);
                }
                verbindung.commit();
                if (!eintraege.isEmpty()) {
                    LOG.infof("Journal-Relay: %d Eintraege versendet", eintraege.size());
                }
            } catch (SQLException | RuntimeException e) {
                try {
                    verbindung.rollback();
                } catch (SQLException rollbackFehler) {
                    e.addSuppressed(rollbackFehler);
                }
                LOG.warnf(e, "Journal-Relay: Durchlauf gescheitert, Rueckstand wird nachgeholt");
            }
        } catch (SQLException e) {
            LOG.warnf(e, "Journal-Relay: Verbindung zur Outbox nicht moeglich");
        }
    }

    /**
     * Sendet einen Eintrag im eigenen Span, der per Span-Link mit dem
     * gespeicherten traceparent (Annahme-Span) verknuepft ist; der
     * Versand propagiert diesen Kontext als Kafka-Header.
     */
    private void versenden(Connection verbindung, Journaleintrag eintrag,
                           OffsetDateTime gesendetAm) throws SQLException {
        SpanBuilder builder = tracer.spanBuilder("journal.relay")
                .setAttribute("durchlauferhitzer.auftrag.id", eintrag.auftragsId());
        W3cTraceKontext.spanContext(eintrag.traceparent()).ifPresent(builder::addLink);
        Span span = builder.startSpan();
        try (Scope ignoriert = span.makeCurrent()) {
            versand.senden(eintrag.auftragsId(), eintrag.payload());
            outboxRepository.gesendetMarkieren(verbindung, eintrag.id(), gesendetAm);
        } finally {
            span.end();
        }
    }
}
