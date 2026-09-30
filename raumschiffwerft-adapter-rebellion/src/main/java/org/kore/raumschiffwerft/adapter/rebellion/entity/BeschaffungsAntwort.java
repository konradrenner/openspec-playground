package org.kore.raumschiffwerft.adapter.rebellion.entity;

/**
 * Antwort der Rebellen-Beschaffungs-API auf das Anlegen einer Beschaffung.
 */
public record BeschaffungsAntwort(String beschaffungsId, String status) {
}
