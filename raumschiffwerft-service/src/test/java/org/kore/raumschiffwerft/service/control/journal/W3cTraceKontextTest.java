package org.kore.raumschiffwerft.service.control.journal;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.opentelemetry.api.trace.SpanContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class W3cTraceKontextTest {

    private static final String TRACEPARENT =
            "00-0af7651916cd43dd8448eb4c9cbbfda1-0af7651916cd43dd-01";
    private static final String TRACE_ID = "0af7651916cd43dd8448eb4c9cbbfda1";
    private static final String SPAN_ID = "0af7651916cd43dd";

    @Test
    void gueltigerTraceparentWirdGeparst() {
        Optional<SpanContext> kontext = W3cTraceKontext.spanContext(TRACEPARENT);

        assertTrue(kontext.isPresent());
        assertEquals(TRACE_ID, kontext.get().getTraceId());
        assertEquals(SPAN_ID, kontext.get().getSpanId());
        assertTrue(kontext.get().isSampled());
        assertTrue(kontext.get().isRemote());
    }

    @Test
    void ungueltigeEingabenLiefernLeer() {
        assertTrue(W3cTraceKontext.spanContext(null).isEmpty());
        assertTrue(W3cTraceKontext.spanContext("").isEmpty());
        assertTrue(W3cTraceKontext.spanContext("kein-w3c-header").isEmpty());
        assertTrue(W3cTraceKontext.spanContext("ff-" + TRACE_ID + "-" + SPAN_ID + "-01").isEmpty());
        assertTrue(W3cTraceKontext.spanContext("00-zu-kurz-" + SPAN_ID + "-01").isEmpty());
        assertTrue(W3cTraceKontext.spanContext("00-" + TRACE_ID + "-zu-kurz-01").isEmpty());
        assertTrue(W3cTraceKontext.spanContext("00-" + TRACE_ID + "-" + SPAN_ID).isEmpty());
        assertFalse(W3cTraceKontext.spanContext(TRACEPARENT).isEmpty());
    }

    @Test
    void formatierungIstRundlaufFaehig() {
        SpanContext kontext = W3cTraceKontext.spanContext(TRACEPARENT).orElseThrow();

        assertEquals(TRACEPARENT, W3cTraceKontext.traceparent(kontext));
    }
}
