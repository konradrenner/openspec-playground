package org.kore.raumschiffwerft.service.control;

import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;

/**
 * Port der Abgleichssteuerung auf die Statusabfrage eines Zielsystems.
 * Die Implementierung liegt im boundary-Package des Service (Camel-Route
 * direct:statusAbfragen); die Steuerung selbst kennt weder Camel noch
 * die Adapter.
 */
public interface Abgleichsport {

    /**
     * Fragt den Verarbeitungsstatus des Auftrags am Zielsystem ab. Jeder
     * technische Misserfolg wird als {@link ZustellungUngeklaert} gemeldet.
     */
    Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId, Zielsystemtyp zielsystemtyp)
            throws ZustellungUngeklaert;
}
