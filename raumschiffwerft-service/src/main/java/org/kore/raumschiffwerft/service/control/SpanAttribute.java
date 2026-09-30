package org.kore.raumschiffwerft.service.control;

import io.opentelemetry.api.trace.Span;

/**
 * Setzt Span-Attribute (durchlauferhitzer.*) auf dem aktuellen Span.
 * Ohne aktiven Span (z. B. Unit-Tests ohne OpenTelemetry) no-op.
 */
public final class SpanAttribute {

    private SpanAttribute() {
    }

    public static void setzen(String name, String wert) {
        Span aktueller = Span.current();
        if (wert != null && aktueller.getSpanContext().isValid()) {
            aktueller.setAttribute(name, wert);
        }
    }
}
