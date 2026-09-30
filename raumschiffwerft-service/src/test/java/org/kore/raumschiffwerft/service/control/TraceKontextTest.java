package org.kore.raumschiffwerft.service.control;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceKontextTest {

    private static final String TRACEPARENT = "00-0af7651916cd43dd8448eb4c9cbbfda1-0af7651916cd43dd-01";
    private static final String TRACE_ID = "0af7651916cd43dd8448eb4c9cbbfda1";

    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(io.opentelemetry.sdk.trace.SdkTracerProvider.builder().build())
            .build();
    private final Tracer tracer = sdk.getTracer("test");

    private Span span;
    private io.opentelemetry.context.Scope scope;

    @BeforeEach
    void spanOeffnen() {
        span = tracer.spanBuilder("annahme").startSpan();
        scope = span.makeCurrent();
    }

    @AfterEach
    void spanSchliessen() {
        scope.close();
        span.end();
    }

    @Test
    void aktiverSpanLiefertSeineTraceIdUndSpanparent() {
        TraceKontext kontext = new TraceKontext(OpenTelemetry.noop());

        assertEquals(span.getSpanContext().getTraceId(), kontext.traceId(TRACEPARENT));
        assertEquals(TraceKontext.traceparentVon(span.getSpanContext()), kontext.traceparent(TRACEPARENT));
    }

    @Test
    void ohneAktivenSpanGreiftDerHeaderFallback() {
        scope.close();
        span.end();
        TraceKontext kontext = new TraceKontext(OpenTelemetry.noop());

        assertEquals(TRACE_ID, kontext.traceId(TRACEPARENT));
        assertEquals(TRACEPARENT, kontext.traceparent(TRACEPARENT));
        assertNull(kontext.traceId(null));
        assertNull(kontext.traceparent(null));
        assertNull(kontext.traceId("kein-w3c"));
    }

    @Test
    void traceparentIstW3cFormat() {
        TraceKontext kontext = new TraceKontext(OpenTelemetry.noop());

        String traceparent = kontext.traceparent(TRACEPARENT);

        String[] teile = traceparent.split("-");
        assertEquals(4, teile.length);
        assertEquals(span.getSpanContext().getTraceId(), teile[1]);
        assertEquals(span.getSpanContext().getSpanId(), teile[2]);
        assertEquals(2, teile[3].length());
    }
}
