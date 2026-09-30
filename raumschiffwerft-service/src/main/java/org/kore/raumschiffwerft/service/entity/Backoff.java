package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Random;

/**
 * Berechnet den Backoff-Zeitpunkt des naechsten Abgleichversuchs:
 * min(basis * 2^(versuche - 1), cap), versehen mit einem Jitter von bis
 * zu jitterAnteil (0.2 heisst bis zu 20 Prozent) auf dem gedeckelten Wert.
 * Die versuche sind die gezaehlten Versuche nach dem Beanspruchen.
 */
public final class Backoff {

    /** Deckelt den Exponenten, damit die Multiplikation nicht ueberlaeuft. */
    private static final int MAX_EXPONENT = 30;

    private final long basisSekunden;
    private final long maxSekunden;
    private final double jitterAnteil;
    private final Random random;

    public Backoff(long basisSekunden, long maxSekunden, double jitterAnteil, Random random) {
        if (basisSekunden < 0 || maxSekunden < 0) {
            throw new IllegalArgumentException("Basis und Cap duerfen nicht negativ sein");
        }
        if (jitterAnteil < 0 || jitterAnteil > 1) {
            throw new IllegalArgumentException("Jitter-Anteil muss zwischen 0 und 1 liegen");
        }
        this.basisSekunden = basisSekunden;
        this.maxSekunden = maxSekunden;
        this.jitterAnteil = jitterAnteil;
        this.random = Objects.requireNonNull(random);
    }

    public OffsetDateTime naechsterVersuchUm(OffsetDateTime jetzt, int versuche) {
        int exponent = Math.min(Math.max(versuche - 1, 0), MAX_EXPONENT);
        long gedeckelt = Math.min(basisSekunden << exponent, maxSekunden);
        double jitter = 1.0 + random.nextDouble() * jitterAnteil;
        long nanos = Math.round(gedeckelt * jitter * 1_000_000_000L);
        return jetzt.plusNanos(nanos);
    }
}
