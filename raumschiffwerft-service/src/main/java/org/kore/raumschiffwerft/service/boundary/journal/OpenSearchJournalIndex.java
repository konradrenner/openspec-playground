package org.kore.raumschiffwerft.service.boundary.journal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.kore.raumschiffwerft.service.control.journal.JournalIndex;

/**
 * Boundary-Implementierung der Journal-Indexierung: speichert den
 * Journaleintrag per OpenSearch-REST-Client als Dokument unter der
 * Auftrags-ID (idempotent).
 */
@ApplicationScoped
public class OpenSearchJournalIndex implements JournalIndex {

    private final OpenSearchIndexClient client;
    private final String index;

    @Inject
    public OpenSearchJournalIndex(@RestClient OpenSearchIndexClient client,
                                  @ConfigProperty(name = "journal.index") String index) {
        this.client = client;
        this.index = index;
    }

    @Override
    public void speichern(String auftragsId, String payload) {
        client.dokumentSpeichern(index, auftragsId, payload);
    }
}
