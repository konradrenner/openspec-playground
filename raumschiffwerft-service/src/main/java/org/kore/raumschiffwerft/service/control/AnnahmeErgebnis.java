package org.kore.raumschiffwerft.service.control;

import org.kore.raumschiffwerft.service.entity.Kaufauftrag;

/**
 * Ergebnis der Annahme: der Auftragsstand (neu angelegt oder bereits
 * vorhanden) und ob es sich um eine Neuannahme handelte.
 */
public record AnnahmeErgebnis(Kaufauftrag auftrag, boolean neu) {
}
