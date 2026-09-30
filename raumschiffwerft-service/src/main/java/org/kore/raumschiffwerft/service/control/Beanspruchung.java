package org.kore.raumschiffwerft.service.control;

import java.util.Objects;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;

/**
 * Eine von der Abgleich-Route atomar beanspruchte Zustellung (bereits im
 * Zustand IN_ABGLEICH mit neuer Lease und erhoehtem Versuchszaehler),
 * zusammen mit dem Ausgangsstatus der Beanspruchung (Basis fuer den
 * Lease-Counter: IN_ZUSTELLUNG/IN_ABGLEICH heisst Absturz-Uebernahme)
 * und dem per Join gelesenen kanonischen Auftrag.
 */
public record Beanspruchung(Zustellung zustellung, Zustellungsstatus ausgangsstatus,
                            Kaufauftrag kaufauftrag) {

    public Beanspruchung {
        Objects.requireNonNull(zustellung);
        Objects.requireNonNull(ausgangsstatus);
        Objects.requireNonNull(kaufauftrag);
    }
}
