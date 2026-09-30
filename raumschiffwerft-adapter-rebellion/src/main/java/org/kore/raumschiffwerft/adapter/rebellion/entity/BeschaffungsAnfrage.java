package org.kore.raumschiffwerft.adapter.rebellion.entity;

/**
 * Anfrage zum Anlegen einer Beschaffung bei der Rebellen-Beschaffungs-API.
 */
public record BeschaffungsAnfrage(String referenz, String auftraggeber, String schiffstyp, int menge,
                                  int zielplanet) {
}
