package org.kore.raumschiffwerft.model.entity;

/**
 * Kanonischer Kaufauftrag: Wer kauft was, wieviel und wohin.
 */
public record Kaufauftrag(String kaeufer, Sternenzerstoererklasse klasse, int anzahl, int lieferplanet) {

    private static final int KAEUFER_MAX = 100;
    private static final int ANZAHL_MIN = 1;
    private static final int ANZAHL_MAX = 12;
    private static final int LIEFERPLANET_MIN = 1;
    private static final int LIEFERPLANET_MAX = 60;

    public Kaufauftrag {
        if (kaeufer == null || kaeufer.isBlank() || kaeufer.length() > KAEUFER_MAX) {
            throw new IllegalArgumentException(
                    "kaeufer muss 1 bis %d Zeichen lang sein, war: %s".formatted(KAEUFER_MAX, kaeufer));
        }
        if (klasse == null) {
            throw new IllegalArgumentException("klasse darf nicht null sein");
        }
        if (anzahl < ANZAHL_MIN || anzahl > ANZAHL_MAX) {
            throw new IllegalArgumentException(
                    "anzahl muss zwischen %d und %d liegen, war: %d".formatted(ANZAHL_MIN, ANZAHL_MAX, anzahl));
        }
        if (lieferplanet < LIEFERPLANET_MIN || lieferplanet > LIEFERPLANET_MAX) {
            throw new IllegalArgumentException(
                    "lieferplanet muss zwischen %d und %d liegen, war: %d"
                            .formatted(LIEFERPLANET_MIN, LIEFERPLANET_MAX, lieferplanet));
        }
    }
}
