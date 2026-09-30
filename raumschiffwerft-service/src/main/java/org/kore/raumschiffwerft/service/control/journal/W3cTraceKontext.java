package org.kore.raumschiffwerft.service.control.journal;

import java.util.Optional;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

/**
 * W3C-traceparent-Format (00-&lt;32 Hex traceId&gt;-&lt;16 Hex spanId&gt;-&lt;2 Hex&gt;)
 * der Journal-Kette: parsen fuer den Span-Link des Relays und formatieren
 * fuer die Kafka-Header-Propagierung.
 */
public final class W3cTraceKontext {

    private W3cTraceKontext() {
    }

    /** Parst einen traceparent in einen remote-SpanContext; leer bei ungueltigem Format. */
    public static Optional<SpanContext> spanContext(String traceparent) {
        if (traceparent == null) {
            return Optional.empty();
        }
        String[] teile = traceparent.trim().split("-");
        if (teile.length != 4
                || !teile[0].equals("00")
                || !teile[1].matches("[0-9a-fA-F]{32}")
                || !teile[2].matches("[0-9a-fA-F]{16}")
                || !teile[3].matches("[0-9a-fA-F]{2}")) {
            return Optional.empty();
        }
        return Optional.of(SpanContext.createFromRemoteParent(
                teile[1].toLowerCase(), teile[2].toLowerCase(),
                "01".equals(teile[3].toLowerCase())
                        ? TraceFlags.getSampled() : TraceFlags.getDefault(),
                TraceState.getDefault()));
    }

    /** Formatiert einen SpanContext als traceparent-String. */
    public static String traceparent(SpanContext kontext) {
        return "00-" + kontext.getTraceId() + "-" + kontext.getSpanId() + "-"
                + (kontext.isSampled() ? "01" : "00");
    }
}
