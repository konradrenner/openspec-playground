package org.kore.raumschiffwerft.service.boundary.integration.journal;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;
import org.kore.raumschiffwerft.service.control.journal.JournalVersand;
import org.kore.raumschiffwerft.service.control.journal.W3cTraceKontext;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Boundary-Implementierung des Journal-Versands: ruft synchron die
 * Camel-Route direct:journalVersenden auf, die den Eintrag an das
 * Kafka-Topic sendet (acks=all, siehe JournalRelayRoute). Der
 * Nachrichten-Key ist die Auftrags-ID; jeder technische Misserfolg
 * wird als RuntimeException gemeldet.
 */
@ApplicationScoped
public class CamelJournalVersand implements JournalVersand {

    private final CamelContext camelContext;

    private ProducerTemplate producerTemplate;

    @Inject
    public CamelJournalVersand(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @PostConstruct
    void producerAufbauen() {
        this.producerTemplate = camelContext.createProducerTemplate();
    }

    @Override
    public void senden(String auftragsId, String payload) {
        Exchange exchange = producerTemplate.send("direct:journalVersenden", ex -> {
            ex.getMessage().setHeader(KafkaConstants.KEY, auftragsId);
            ex.getMessage().setHeader("traceparent", aktuellerTraceparent());
            ex.getMessage().setBody(payload);
        });

        Throwable fehler = exchange.getException();
        if (fehler != null) {
            throw new IllegalStateException(
                    "Kafka-Versand des Journaleintrags gescheitert (auftragsId=%s)"
                            .formatted(auftragsId), alsException(fehler));
        }
    }

    /** traceparent des aktiven Relay-Spans; ohne aktiven Span kein Header. */
    private String aktuellerTraceparent() {
        SpanContext kontext = io.opentelemetry.api.trace.Span.current().getSpanContext();
        return kontext.isValid() ? W3cTraceKontext.traceparent(kontext) : null;
    }

    private Exception alsException(Throwable fehler) {
        if (fehler instanceof Exception e) {
            return e;
        }
        return new IllegalStateException(fehler);
    }
}
