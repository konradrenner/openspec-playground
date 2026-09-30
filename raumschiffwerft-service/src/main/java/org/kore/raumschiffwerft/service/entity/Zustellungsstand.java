package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;

/**
 * Stand einer einzelnen Zustellung in der Abfrage-Antwort (GET).
 */
public record Zustellungsstand(String zielsystem, String status, String externeReferenz, int versuche,
                               OffsetDateTime naechsterVersuchUm) {
}
