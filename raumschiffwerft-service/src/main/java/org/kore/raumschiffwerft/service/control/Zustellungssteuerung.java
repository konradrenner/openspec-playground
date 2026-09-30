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

/**
 * Zustellungssteuerung: liefert nach dem Commit der Annahme jede
 * Zustellung synchron ueber den Zustellport aus (das kanonische Modell
 * kommt aus dem Speicher, kein DB-Select) und verbucht den Ausgang mit
 * GENAU EINEM konditionalen Update pro Zustellung. Der Adapter selbst
 * verbucht nie.
 */
@ApplicationScoped
public class Zustellungssteuerung {

    private static final Logger LOG = Logger.getLogger(Zustellungssteuerung.class);

    private final Zustellport zustellport;
    private final ZustellungRepository zustellungRepository;
    private final long wiederholungSekunden;

    @Inject
    public Zustellungssteuerung(Zustellport zustellport, ZustellungRepository zustellungRepository,
                                @ConfigProperty(name = "zustellung.wiederholung-sekunden") long wiederholungSekunden) {
        this.zustellport = zustellport;
        this.zustellungRepository = zustellungRepository;
        this.wiederholungSekunden = wiederholungSekunden;
    }

    public void zustellen(Kaufauftrag auftrag) {
        for (Zustellung zustellung : auftrag.zustellungen()) {
            Zustellungsstatus ausgangsstatus = zustellung.status();
            try {
                Zustellbestaetigung bestaetigung = zustellport.zustellen(zustellung.auftragsId(),
                        auftrag.kanonischerAuftrag(), zustellung.zielsystem());
                zustellung.bestaetigen(bestaetigung.externeReferenz());
            } catch (ZustellungUngeklaert e) {
                LOG.warnf(e, "Zustellung an %s gescheitert (auftragsId=%s), als UNGEKLAERT verbucht",
                        zustellung.zielsystem(), zustellung.auftragsId().wert());
                zustellung.ungeklaertErklaeren(OffsetDateTime.now().plusSeconds(wiederholungSekunden));
            }
            verbuchen(zustellung, ausgangsstatus);
        }
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
