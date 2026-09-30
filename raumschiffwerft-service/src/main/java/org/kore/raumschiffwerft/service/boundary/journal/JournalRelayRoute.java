package org.kore.raumschiffwerft.service.boundary.journal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.service.control.journal.JournalRelay;

/**
 * Journal-Relay-Route: der Timer triggert periodisch den Relay-Durchlauf
 * (Outbox in Batches beanspruchen, synchron senden, gesendet_am setzen).
 * direct:journalVersenden sendet einen Eintrag an das Topic mit Key =
 * Auftrags-ID und acks=all - der Producer wartet auf die Bestaetigung
 * der Broker (idempotent konfiguriert, siehe application.properties).
 */
@ApplicationScoped
public class JournalRelayRoute extends RouteBuilder {

    private final JournalRelay journalRelay;
    private final long timerPeriodMillis;
    private final String topic;
    private final String brokers;

    @Inject
    public JournalRelayRoute(JournalRelay journalRelay,
                              @ConfigProperty(name = "journal.relay.timer-period-millis")
                              long timerPeriodMillis,
                              @ConfigProperty(name = "journal.topic") String topic,
                              @ConfigProperty(name = "kafka.bootstrap.servers") String brokers) {
        this.journalRelay = journalRelay;
        this.timerPeriodMillis = timerPeriodMillis;
        this.topic = topic;
        this.brokers = brokers;
    }

    @Override
    public void configure() {
        from("timer:journal-relay?period=" + timerPeriodMillis).routeId("journal-relay")
                .process(exchange -> journalRelay.relay());

        from("direct:journalVersenden").routeId("journalVersenden")
                .toF("kafka:%s?brokers=%s&requestRequiredAcks=all", topic, brokers);
    }
}
