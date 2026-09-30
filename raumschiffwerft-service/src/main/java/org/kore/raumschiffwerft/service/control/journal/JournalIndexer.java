package org.kore.raumschiffwerft.service.control.journal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Indexiert Journaleintraege in OpenSearch: Auftrags-ID als
 * Dokument-ID macht Wiederholungen idempotent (ueberschreiben statt
 * Duplikat). Ein Indexierfehler wird weitergereicht, damit die
 * Consumer-Route den Kafka-Offset NICHT committet und der Eintrag
 * erneut konsumiert wird.
 */
@ApplicationScoped
public class JournalIndexer {

    private final JournalIndex index;

    @Inject
    public JournalIndexer(JournalIndex index) {
        this.index = index;
    }

    public void indexiere(String auftragsId, String payload) {
        try {
            index.speichern(auftragsId, payload);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Journal-Indexierung gescheitert (auftragsId=%s)".formatted(auftragsId), e);
        }
    }
}
