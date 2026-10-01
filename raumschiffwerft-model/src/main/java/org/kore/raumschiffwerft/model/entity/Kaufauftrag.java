package org.kore.raumschiffwerft.model.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Kanonischer Kaufauftrag: Wer kauft was, wieviel und wohin. Die Wertebereiche
 * sind als Bean-Validation-Constraints deklariert und werden von den
 * verarbeitenden Stellen geprueft (Annahme, Zustell-Route), nicht im Konstruktor.
 */
public record Kaufauftrag(
        @NotBlank @Size(min = 1, max = 100) String kaeufer,
        @NotNull Sternenzerstoererklasse klasse,
        @Min(1) @Max(12) int anzahl,
        @Min(1) @Max(60) int lieferplanet) {
}
