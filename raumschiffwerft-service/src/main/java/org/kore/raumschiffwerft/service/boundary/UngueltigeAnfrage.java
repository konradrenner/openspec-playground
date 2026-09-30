package org.kore.raumschiffwerft.service.boundary;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

/**
 * Ungueltige Annahme (fehlender/kein UUID-faehiger Idempotency-Key,
 * unlesbarer Body oder Bean-Validation-Verstoss) -> 400, ohne dass
 * etwas gespeichert oder zugestellt wird.
 */
public class UngueltigeAnfrage extends BadRequestException {

    public UngueltigeAnfrage(String meldung) {
        super(Response.status(Response.Status.BAD_REQUEST).entity(meldung).build());
    }
}
