package org.kore.raumschiffwerft.service.control;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.kore.raumschiffwerft.service.entity.Kaufauftrag;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Zustellungssteuerung: liefert nach dem Commit der Annahme jede
 * Zustellung synchron ueber den Zustellport aus (das kanonische Modell
 * kommt aus dem Speicher, kein DB-Select) und verbucht den Ausgang mit
 * GENAU EINEM konditionalen Update pro Zustellung. Der Adapter selbst
 * verbucht nie. Span-Attribute (auftrag.id, zielsystem, ergebnis) und
 * der Ergebnis-Counter werden hier gepflegt.
 */
@ApplicationScoped
public class Zustellungssteuerung {

    private static final Logger LOG = Logger.getLogger(Zustellungssteuerung.class);

    private final Zustellport zustellport;
    private final ZustellungRepository zustellungRepository;
    private final MeterRegistry meterRegistry;
    private final long wiederholungSekunden;

    @Inject
    public Zustellungssteuerung(Zustellport zustellport, ZustellungRepository zustellungRepository,
                                MeterRegistry meterRegistry,
                                @ConfigProperty(name = "zustellung.wiederholung-sekunden") long wiederholungSekunden) {
        this.zustellport = zustellport;
        this.zustellungRepository = zustellungRepository;
        this.meterRegistry = meterRegistry;
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
                LOG.warnf(e, "Zustellung an %s gescheitert (auftragsId=%s), als UNGEKLAERT verbucht",
                        zustellung.zielsystem(), zustellung.auftragsId().wert());
                zustellung.ungeklaertErklaeren(OffsetDateTime.now().plusSeconds(wiederholungSekunden));
            }
            SpanAttribute.setzen("durchlauferhitzer.zustellstatus", zustellung.status().name());
            verbuchen(zustellung, ausgangsstatus);
            zaehlen(zustellung);
        }
    }

    private void zaehlen(Zustellung zustellung) {
        meterRegistry.counter("durchlauferhitzer.zustellungen",
                        "zielsystem", zustellung.zielsystem().name(),
                        "ergebnis", zustellung.status().name())
                .increment();
    }

    private void verbuchen(Zustellung zustellung, Zustellungsstatus erwarteterAusgangsstatus) {
        try {
            zustellungRepository.verbuchen(zustellung, erwarteterAusgangsstatus);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Verbuchen der Zustellung gescheitert (auftragsId=%s, zielsystem=%s)"
                            .formatted(zustellung.auftragsId().wert(), zustellung.zielsystem()), e);
        }
    }
}
