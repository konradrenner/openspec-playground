package org.kore.raumschiffwerft.service.control;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Liefert den Trace-Kontext der Annahme: bevorzugt aus dem aktiven Span
 * (OpenTelemetry, auch ohne eingehenden traceparent-Header), im Rueckfall
 * aus dem W3C-traceparent-Header des Requests
 * (Format: 00-&lt;32 Hex traceId&gt;-&lt;16 Hex spanId&gt;-&lt;2 Hex&gt;).
 */
@ApplicationScoped
public class TraceKontext {

    private final OpenTelemetry openTelemetry;

    @Inject
    public TraceKontext(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    /** Trace-ID des aktiven Spans; ohne aktiven Span aus dem Header. */
    public String traceId(String traceparent) {
        SpanContext kontext = Span.current().getSpanContext();
        if (kontext.isValid()) {
            return kontext.getTraceId();
        }
        return traceIdAusHeader(traceparent);
    }

    /** traceparent-String des aktiven Spans; ohne aktiven Span der Header roh. */
    public String traceparent(String traceparent) {
        SpanContext kontext = Span.current().getSpanContext();
        if (kontext.isValid()) {
            return traceparentVon(kontext);
        }
        return traceparent;
    }

    static String traceparentVon(SpanContext kontext) {
        return "00-" + kontext.getTraceId() + "-" + kontext.getSpanId() + "-"
                + (kontext.isSampled() ? "01" : "00");
    }

    static String traceIdAusHeader(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] teile = traceparent.trim().split("-");
        if (teile.length != 4 || !teile[1].matches("[0-9a-fA-F]{32}")) {
            return null;
        }
        return teile[1];
    }
}
