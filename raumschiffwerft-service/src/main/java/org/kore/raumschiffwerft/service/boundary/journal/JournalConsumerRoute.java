package org.kore.raumschiffwerft.service.boundary.journal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.service.control.journal.JournalIndexer;

/**
 * Journal-Consumer-Route: konsumiert das Topic, indexiert jeden
 * Journaleintrag in OpenSearch (Auftrags-ID als Dokument-ID) und
 * bestätigt den Kafka-Offset MANUELL - erst nach erfolgreichem
 * Indexieren. Schlägt das Indexieren fehl, wird der Eintrag nach dem
 * Neustart/Weiterlaufen erneut konsumiert (at-least-once, durch die
 * Dokument-ID idempotent).
 */
@ApplicationScoped
public class JournalConsumerRoute extends RouteBuilder {

    private final JournalIndexer journalIndexer;
    private final String topic;
    private final String brokers;
    private final String consumerGruppe;

    @Inject
    public JournalConsumerRoute(JournalIndexer journalIndexer,
                                @ConfigProperty(name = "journal.topic") String topic,
                                @ConfigProperty(name = "kafka.bootstrap.servers") String brokers,
                                @ConfigProperty(name = "journal.consumer-group") String consumerGruppe) {
        this.journalIndexer = journalIndexer;
        this.topic = topic;
        this.brokers = brokers;
        this.consumerGruppe = consumerGruppe;
    }

    @Override
    public void configure() {
        from("kafka:" + topic
                + "?brokers=" + brokers
                + "&groupId=" + consumerGruppe
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&allowManualCommit=true").routeId("journalIndexierung")
                .process(exchange -> {
                    String auftragsId = exchange.getIn().getHeader(KafkaConstants.KEY, String.class);
                    String payload = exchange.getIn().getBody(String.class);
                    journalIndexer.indexiere(auftragsId, payload);
                    exchange.getIn().getHeader(KafkaConstants.MANUAL_COMMIT,
                            org.apache.camel.component.kafka.consumer.KafkaManualCommit.class).commit();
                });
    }
}
