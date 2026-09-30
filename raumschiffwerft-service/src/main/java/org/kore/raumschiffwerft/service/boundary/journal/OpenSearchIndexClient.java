package org.kore.raumschiffwerft.service.boundary.journal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST-Client auf die OpenSearch-HTTP-API (Index pruefen und anlegen,
 * Dokument speichern). Nur die drei noetigen Aufrufe - bewusst kein
 * schwerer OpenSearch-Java-Client (KISS).
 */
@RegisterRestClient(configKey = "journal-opensearch")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface OpenSearchIndexClient {

    /** Liefert 200, wenn der Index existiert, sonst 404. */
    @GET
    @Path("/{index}")
    Response indexPruefen(@PathParam("index") String index);

    /** Legt den Index mit dem expliziten Mapping an. */
    @PUT
    @Path("/{index}")
    Response indexAnlegen(@PathParam("index") String index, String mapping);

    /** Speichert den Journaleintrag unter der Auftrags-ID als Dokument-ID. */
    @PUT
    @Path("/{index}/_doc/{dokumentId}")
    void dokumentSpeichern(@PathParam("index") String index,
                           @PathParam("dokumentId") String dokumentId,
                           String payload);
}
