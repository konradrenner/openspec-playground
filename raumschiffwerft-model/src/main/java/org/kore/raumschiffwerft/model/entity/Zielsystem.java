package org.kore.raumschiffwerft.model.entity;

import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;

/**
 * Port auf ein Zielsystem (Imperium oder Rebellion): Implementierungen
 * liefern Auftraege aus und melden jeden Misserfolg als
 * {@link ZustellungUngeklaert} - ohne Retries und ohne Datenbankzugriff.
 */
public interface Zielsystem {

    Zielsystemtyp typ();

    Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag auftrag) throws ZustellungUngeklaert;

    Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId) throws ZustellungUngeklaert;
}
