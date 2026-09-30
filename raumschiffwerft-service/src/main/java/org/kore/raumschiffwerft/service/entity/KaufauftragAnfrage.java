package org.kore.raumschiffwerft.service.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Anfrage-Nachricht fuer die Annahme eines Kaufauftrags.
 * Der Pflicht-Header Idempotency-Key (UUID) wird separat geprueft.
 */
public record KaufauftragAnfrage(
        @NotBlank @Size(min = 1, max = 100) String kaeufer,
        @NotBlank @Pattern(regexp = "IMPERIAL_I|IMPERIAL_II|VICTORY|EXECUTOR") String klasse,
        @Min(1) @Max(12) int anzahl,
        @Min(1) @Max(60) int lieferplanet) {
}
