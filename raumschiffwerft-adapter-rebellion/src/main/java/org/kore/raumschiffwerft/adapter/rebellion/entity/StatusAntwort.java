package org.kore.raumschiffwerft.adapter.rebellion.entity;

/**
 * Antwort der Rebellen-Beschaffungs-API auf eine Statusabfrage
 * (IN_ARBEIT, ERLEDIGT oder UNBEKANNT).
 */
public record StatusAntwort(String status) {
}
