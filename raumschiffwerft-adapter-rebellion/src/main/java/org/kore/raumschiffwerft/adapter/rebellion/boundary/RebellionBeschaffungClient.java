package org.kore.raumschiffwerft.adapter.rebellion.boundary;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAnfrage;
import org.kore.raumschiffwerft.adapter.rebellion.entity.BeschaffungsAntwort;
import org.kore.raumschiffwerft.adapter.rebellion.entity.StatusAntwort;

/**
 * MicroProfile-REST-Client auf die Beschaffungs-API der Rebellion.
 */
@RegisterRestClient(configKey = "rebellion")
@Path("/api/v1")
public interface RebellionBeschaffungClient {

    @POST
    @Path("/beschaffungen")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    BeschaffungsAntwort beschaffungAnlegen(BeschaffungsAnfrage anfrage);

    @GET
    @Path("/beschaffungen/{referenz}/status")
    @Produces(MediaType.APPLICATION_JSON)
    StatusAntwort statusAbfragen(@PathParam("referenz") String referenz);
}
