package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Stand eines Auftrags in der Abfrage-Antwort (GET): Daten aus auftrag
 * und allen zustellung-Zeilen.
 */
public record Auftragsstand(String auftragsId, OffsetDateTime angenommenAm, Object kaufauftrag,
                            List<Zustellungsstand> zustellungen) {
}
