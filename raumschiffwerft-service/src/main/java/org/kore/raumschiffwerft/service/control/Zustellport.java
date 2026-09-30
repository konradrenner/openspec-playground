package org.kore.raumschiffwerft.service.control;

import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;

/**
 * Port der Zustellungssteuerung auf das gewaehlte Zielsystem. Die
 * Implementierung liegt im boundary-Package des Service (Camel-Route
 * direct:zustellen); die Steuerung selbst kennt weder Camel noch die
 * Adapter.
 */
public interface Zustellport {

    /**
     * Stellt den Auftrag am Zielsystem zu. Jeder Misserfolg wird als
     * {@link ZustellungUngeklaert} gemeldet.
     */
    Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag kaufauftrag,
                                  Zielsystemtyp zielsystemtyp) throws ZustellungUngeklaert;
}
