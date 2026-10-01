package org.kore.raumschiffwerft.service.control;

import java.time.OffsetDateTime;
import java.util.logging.Level;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.kore.raumschiffwerft.service.entity.Kaufauftrag;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;

/**
 * Zustellungssteuerung: liefert nach dem Commit der Annahme jede
 * Zustellung synchron ueber den Zustellport aus (das kanonische Modell
 * kommt aus dem Speicher, kein DB-Select) und verbucht den Ausgang mit
 * GENAU EINEM konditionalen Update pro Zustellung. Der Adapter selbst
 * verbucht nie. Span-Attribute (auftrag.id, zielsystem, ergebnis) und
 * der Ergebnis-Counter (OpenTelemetry API) werden hier gepflegt.
 */
@ApplicationScoped
public class Zustellungssteuerung {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Zustellungssteuerung.class.getName());

    private final Zustellport zustellport;
    private final ZustellungRepository zustellungRepository;
    private final LongCounter zustellungsErgebnisse;
    private final long wiederholungSekunden;

    @Inject
    public Zustellungssteuerung(Zustellport zustellport, ZustellungRepository zustellungRepository,
                                OpenTelemetry openTelemetry,
                                @ConfigProperty(name = "zustellung.wiederholung-sekunden") long wiederholungSekunden) {
        this.zustellport = zustellport;
        this.zustellungRepository = zustellungRepository;
        this.zustellungsErgebnisse = openTelemetry.getMeterProvider().get("durchlauferhifter")
                .counterBuilder("durchlauferhifter.zustellungen").build();
        this.wiederholungSekunden = wiederholungSekunden;
    }

    public void zustellen(Kaufauftrag auftrag) {
        for (Zustellung zustellung : auftrag.zustellungen()) {
            Zustellungsstatus ausgangsstatus = zustellung.status();
            SpanAttribute.setzen("durchlauferhitzer.auftrag.id",
                    zustellung.auftragsId().wert().toString());
            SpanAttribute.setzen("durchlauferhitzer.zielsystem", zustellung.zielsystem().name());
            try {
                Zustellbestaetigung bestaetigung = zustellport.zustellen(zustellung.auftragsId(),
                        auftrag.kanonischerAuftrag(), zustellung.zielsystem());
                zustellung.bestaetigen(bestaetigung.externeReferenz());
            } catch (ZustellungUngeklaert e) {
                LOG.log(Level.WARNING, "Zustellung an " + zustellung.zielsystem()
                        + " gescheitert (auftragsId=" + zustellung.auftragsId().wert()
                        + "), als UNGEKLAERT verbucht", e);
                zustellung.ungeklaertErklaeren(OffsetDateTime.now().plusSeconds(wiederholungSekunden));
            }
            SpanAttribute.setzen("durchlauferhitzer.zustellstatus", zustellung.status().name());
            zustellungRepository.verbuchen(zustellung, ausgangsstatus);
            zaehlen(zustellung);
        }
    }

    private void zaehlen(Zustellung zustellung) {
        zustellungsErgebnisse.add(1, Attributes.of(
                AttributeKey.stringKey("zielsystem"), zustellung.zielsystem().name(),
                AttributeKey.stringKey("ergebnis"), zustellung.status().name()));
    }
}
