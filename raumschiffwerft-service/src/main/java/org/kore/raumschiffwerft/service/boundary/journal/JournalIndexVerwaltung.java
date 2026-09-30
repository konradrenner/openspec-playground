package org.kore.raumschiffwerft.service.boundary.journal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Legt den Journal-Index beim Start an, falls er noch nicht existiert -
 * mit explizitem Mapping. rohPayload wird nicht indiziert ("enabled":
 * false): der rohe Body bleibt im Dokument gespeichert, ist aber nicht
 * durchsuchbar. Ein bereits existierender Index bleibt unangetastet.
 */
@ApplicationScoped
public class JournalIndexVerwaltung {

    private static final Logger LOG = Logger.getLogger(JournalIndexVerwaltung.class);

    private final OpenSearchIndexClient client;
    private final String index;

    @Inject
    public JournalIndexVerwaltung(@RestClient OpenSearchIndexClient client,
                                  @ConfigProperty(name = "journal.index") String index) {
        this.client = client;
        this.index = index;
    }

    /** Explizites Mapping des Journal-Index; rohPayload nicht indizierbar. */
    static final String MAPPING = """
            {
              "mappings": {
                "properties": {
                  "schemaVersion": { "type": "long" },
                  "auftragsId":   { "type": "keyword" },
                  "empfangenAm":  { "type": "date" },
                  "traceId":      { "type": "keyword" },
                  "traceparent":  { "type": "keyword" },
                  "kaufauftrag":  { "type": "object" },
                  "rohPayload":   { "enabled": false }
                }
              }
            }
            """;

    void indexAnlegen(@Observes StartupEvent startup) {
        try {
            if (indexExistiert()) {
                return;
            }
            try (Response angelegt = client.indexAnlegen(index, MAPPING)) {
                if (angelegt.getStatus() == Response.Status.OK.getStatusCode()) {
                    LOG.infof("Journal-Index %s mit explizitem Mapping angelegt", index);
                } else {
                    LOG.warnf("Journal-Index %s nicht angelegt, Status %s", index, angelegt.getStatus());
                }
            }
        } catch (RuntimeException e) {
            LOG.warnf(e, "Journal-Index %s konnte beim Start nicht geprueft oder angelegt werden", index);
        }
    }

    private boolean indexExistiert() {
        try (Response antwort = client.indexPruefen(index)) {
            return antwort.getStatus() == Response.Status.OK.getStatusCode();
        } catch (jakarta.ws.rs.WebApplicationException nichtGefunden) {
            // 404: der Index existiert noch nicht und wird angelegt
            return false;
        }
    }
}
