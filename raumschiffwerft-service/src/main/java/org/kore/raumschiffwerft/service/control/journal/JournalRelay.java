package org.kore.raumschiffwerft.service.control.journal;

import java.util.logging.Level;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Journal-Relay: versendet ungesendete Outbox-Zeilen zuverlaessig nach
 * Kafka (at-least-once). Der Outbox-Port kapselt den Durchlauf in einer
 * kurzen Transaktion (beanspruchen, senden, markieren, committen); dieser
 * Relay oeffnet pro Eintrag einen eigenen Span, der per Span-Link mit dem
 * im Journaleintrag gespeicherten traceparent verknuepft ist, und
 * propagiert diesen Kontext als Kafka-Header. Ein Sendefehler rollt die
 * Transaktion zurueck - bereits gesendete Zeilen duerfen dann spaeter
 * erneut gesendet werden (Duplikate sind erlaubt). Ist Kafka nicht
 * erreichbar, bleibt die Outbox unberuehrt und der Rueckstand wird beim
 * naechsten Durchlauf nachgeholt; die Annahme ist davon unberuehrt.
 */
@ApplicationScoped
public class JournalRelay {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(JournalRelay.class.getName());

    private final JournalOutboxRepository outboxRepository;
    private final JournalVersand versand;
    private final Tracer tracer;
    private final int batch;

    @Inject
    public JournalRelay(JournalOutboxRepository outboxRepository, JournalVersand versand,
                        OpenTelemetry openTelemetry,
                        @ConfigProperty(name = "journal.relay.batch") int batch) {
        this.outboxRepository = outboxRepository;
        this.versand = versand;
        this.tracer = openTelemetry.getTracer("durchlauferhitzer.journal");
        this.batch = batch;
    }

    /** Ein Relay-Durchlauf; Fehler werden geloggt, nicht geworfen. */
    public void relay() {
        try {
            int versendet = outboxRepository.versenden(batch, this::eintragVersenden);
            if (versendet > 0) {
                LOG.log(Level.INFO, "Journal-Relay: {0} Eintraege versendet", versendet);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Journal-Relay: Durchlauf gescheitert, Rueckstand wird nachgeholt", e);
        }
    }

    /**
     * Sendet einen Eintrag im eigenen Span, der per Span-Link mit dem
     * gespeicherten traceparent (Annahme-Span) verknuepft ist; der
     * Versand propagiert diesen Kontext als Kafka-Header.
     */
    private void eintragVersenden(Journaleintrag eintrag) {
        SpanBuilder builder = tracer.spanBuilder("journal.relay")
                .setAttribute("durchlauferhitzer.auftrag.id", eintrag.auftragsId());
        W3cTraceKontext.spanContext(eintrag.traceparent()).ifPresent(builder::addLink);
        Span span = builder.startSpan();
        try (Scope ignoriert = span.makeCurrent()) {
            versand.senden(eintrag.auftragsId(), eintrag.payload());
        } finally {
            span.end();
        }
    }
}
