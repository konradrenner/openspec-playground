package org.kore.raumschiffwerft.service.entity;

/**
 * Status einer Zustellung im Lebenszyklus des Auftrags.
 * In diesem Change erreichbar: IN_ZUSTELLUNG -> BESTAETIGT / UNGEKLAERT.
 * IN_ABGLEICH und FEHLGESCHLAGEN sind im Schema vorhanden, haben aber
 * noch keine Uebergaenge (reserviert fuer spaetere Changes).
 */
public enum Zustellungsstatus {
    IN_ZUSTELLUNG,
    IN_ABGLEICH,
    UNGEKLAERT,
    BESTAETIGT,
    FEHLGESCHLAGEN
}
