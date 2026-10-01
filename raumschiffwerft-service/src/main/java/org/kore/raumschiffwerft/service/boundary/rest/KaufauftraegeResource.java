package org.kore.raumschiffwerft.service.boundary.rest;

import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.service.control.AnnahmeController;
import org.kore.raumschiffwerft.service.control.AnnahmeErgebnis;
import org.kore.raumschiffwerft.service.control.AuftragRepository;
import org.kore.raumschiffwerft.service.control.DatenbankNichtErreichbar;
import org.kore.raumschiffwerft.service.control.Zustellungssteuerung;
import org.kore.raumschiffwerft.service.entity.Auftragsstand;
import org.kore.raumschiffwerft.service.entity.KaufauftragAnfrage;
import org.kore.raumschiffwerft.service.entity.Zustellungsstand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST-Schnittstelle der Raumschiffwerft: Annahme (POST) und Abfrage
 * (GET) von Kaufauftraegen. Der Idempotency-Key ist Pflicht und die
 * AuftragsId; die Antwort ist nur bei vollstaendig bestaetigten
 * Zustellungen 200, sonst 202 mit Location.
 */
@ApplicationScoped
@Path("/api/v1/kaufauftraege")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class KaufauftraegeResource {

    private final AnnahmeController annahmeController;
    private final Zustellungssteuerung zustellungssteuerung;
    private final AuftragRepository auftragRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final long retryAfterSekunden;

    @Inject
    public KaufauftraegeResource(AnnahmeController annahmeController,
                                 Zustellungssteuerung zustellungssteuerung,
                                 AuftragRepository auftragRepository,
                                 ObjectMapper objectMapper,
                                 Validator validator,
                                 @ConfigProperty(name = "auftrag.retry-after-seconds") long retryAfterSekunden) {
        this.annahmeController = annahmeController;
        this.zustellungssteuerung = zustellungssteuerung;
        this.auftragRepository = auftragRepository;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.retryAfterSekunden = retryAfterSekunden;
    }

    @POST
    public Response annehmen(String rohPayload,
                             @HeaderParam("Idempotency-Key") String idempotencyKey,
                             @HeaderParam("traceparent") String traceparent) {
        AuftragsId auftragsId = idempotencyKeyPruefen(idempotencyKey);
        KaufauftragAnfrage anfrage = parsen(rohPayload);
        beanValidationVerstoesseAbweisen(anfrage);

        org.kore.raumschiffwerft.model.entity.Kaufauftrag kanonisch = new org.kore.raumschiffwerft.model.entity.Kaufauftrag(
                anfrage.kaeufer(), Sternenzerstoererklasse.valueOf(anfrage.klasse()),
                anfrage.anzahl(), anfrage.lieferplanet());

        AnnahmeErgebnis ergebnis;
        try {
            ergebnis = annahmeController.annehmen(auftragsId, kanonisch, rohPayload, traceparent);
        } catch (DatenbankNichtErreichbar e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .header("Retry-After", Long.toString(retryAfterSekunden))
                    .build();
        }

        // Nach dem Commit synchron zustellen - nur bei Neuannahme
        if (ergebnis.neu()) {
            zustellungssteuerung.zustellen(ergebnis.auftrag());
        }
        return antwort(ergebnis.auftrag());
    }

    @GET
    @Path("/{id}")
    public Response stand(@PathParam("id") UUID id) {
        return auftragRepository.stand(new AuftragsId(id))
                .map(auftrag -> Response.ok(standAus(auftrag)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    private Response antwort(org.kore.raumschiffwerft.service.entity.Kaufauftrag auftrag) {
        if (auftrag.alleBestaetigt()) {
            return Response.ok(standAus(auftrag)).build();
        }
        return Response.accepted()
                .header("Location", "/api/v1/kaufauftraege/" + auftrag.auftragsId().wert())
                .entity(standAus(auftrag))
                .build();
    }

    private Auftragsstand standAus(org.kore.raumschiffwerft.service.entity.Kaufauftrag auftrag) {
        List<Zustellungsstand> zustellungen = auftrag.zustellungen().stream()
                .map(z -> new Zustellungsstand(z.zielsystem().name(), z.status().name(),
                        z.externeReferenz(), z.versuche(), z.naechsterVersuchUm()))
                .toList();
        return new Auftragsstand(auftrag.auftragsId().wert().toString(), auftrag.angenommenAm(),
                auftrag.kanonischerAuftrag(), zustellungen);
    }

    private AuftragsId idempotencyKeyPruefen(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new UngueltigeAnfrage("Idempotency-Key fehlt");
        }
        try {
            return new AuftragsId(UUID.fromString(idempotencyKey.trim()));
        } catch (IllegalArgumentException e) {
            throw new UngueltigeAnfrage("Idempotency-Key ist keine UUID: " + idempotencyKey);
        }
    }

    private KaufauftragAnfrage parsen(String rohPayload) {
        try {
            return objectMapper.readValue(rohPayload, KaufauftragAnfrage.class);
        } catch (JsonProcessingException e) {
            throw new UngueltigeAnfrage("Body ist kein gueltiger Kaufauftrag: " + e.getOriginalMessage());
        }
    }

    private void beanValidationVerstoesseAbweisen(KaufauftragAnfrage anfrage) {
        List<String> verstoesse = validator.validate(anfrage).stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .toList();
        if (!verstoesse.isEmpty()) {
            throw new UngueltigeAnfrage("Body verletzt die Validierungsregeln: " + String.join("; ", verstoesse));
        }
    }
}
