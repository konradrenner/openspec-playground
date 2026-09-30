package org.kore.raumschiffwerft.service.control.aufraeumen;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;

/**
 * Aufbewahrungsregel des Aufraeumens: Ein Auftrag ist loeschbar, wenn
 * seine Annahme aelter ist als die Aufbewahrungsfrist (zugleich das
 * Idempotenzfenster) und ALLE seine Zustellungen BESTAETIGT sind.
 * Fehlgeschlagene, offene oder ungeklaerte Zustellungen halten den
 * Auftrag; FEHLGESCHLAGEN-Auftraege werden damit nie automatisch
 * geloescht.
 */
@ApplicationScoped
public class Aufbewahrungsregel {

    private final Duration aufbewahrung;

    @Inject
    public Aufbewahrungsregel(
            @ConfigProperty(name = "durchlauferhitzer.aufraeumen.aufbewahrung") Duration aufbewahrung) {
        this.aufbewahrung = aufbewahrung;
    }

    /** Beginn des loeschbaren Zeitfensters: alles davor ist alt genug. */
    public OffsetDateTime stichtag(OffsetDateTime jetzt) {
        return jetzt.minus(aufbewahrung);
    }

    /**
     * Entscheidet die Loeschbarkeit eines Auftrags: nicht leer, alle
     * Zustellungen BESTAETIGT, Annahme vor dem Stichtag.
     */
    public boolean loeschbar(Collection<Zustellungsstatus> zustaende,
                             OffsetDateTime angenommenAm, OffsetDateTime jetzt) {
        if (zustaende == null || zustaende.isEmpty() || angenommenAm == null) {
            return false;
        }
        if (!angenommenAm.isBefore(stichtag(jetzt))) {
            return false;
        }
        return zustaende.stream().allMatch(status -> status == Zustellungsstatus.BESTAETIGT);
    }
}
