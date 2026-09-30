package org.kore.raumschiffwerft.service.control;

/**
 * Extrahiert Trace-Kontext aus dem W3C-traceparent-Header des Requests
 * (Format: 00-&lt;32 Hex traceId&gt;-&lt;16 Hex spanId&gt;-&lt;2 Hex&gt;).
 */
final class TraceKontext {

    private TraceKontext() {
    }

    static String traceId(String traceparent) {
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
