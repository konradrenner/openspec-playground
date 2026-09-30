package org.kore.raumschiffwerft.service.entity;

/**
 * Status einer Zustellung im Lebenszyklus des Auftrags.
 * Erstzustellung: IN_ZUSTELLUNG -> BESTAETIGT / UNGEKLAERT.
 * Abgleich: IN_ZUSTELLUNG / UNGEKLAERT / IN_ABGLEICH -> IN_ABGLEICH
 * (Beanspruchen), IN_ABGLEICH -> BESTAETIGT / UNGEKLAERT / FEHLGESCHLAGEN.
 */
public enum Zustellungsstatus {
    IN_ZUSTELLUNG,
    IN_ABGLEICH,
    UNGEKLAERT,
    BESTAETIGT,
    FEHLGESCHLAGEN
}
