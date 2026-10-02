package org.kore.raumschiffwerft.service.boundary;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Test-Exporter der Integrationstests: Quarkus registriert jeden CDI-Bean
 * vom Typ SpanExporter im SDK - dieser hier behaelt alle exportierten Spans
 * im Speicher, damit Tests sie auswerten koennen (zusätzlich zum OTLP-Export).
 */
@ApplicationScoped
public class SpansAufnehmenderExporter implements SpanExporter {

    private final List<SpanData> spans = new CopyOnWriteArrayList<>();

    /** Alle exportierten Spans. */
    public List<SpanData> spans() {
        return List.copyOf(spans);
    }

    /** Erster exportierter Span mit dem Namen; leer, wenn es keinen gibt. */
    public java.util.Optional<SpanData> span(String name) {
        return spans.stream().filter(s -> s.getName().equals(name)).findFirst();
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        this.spans.addAll(spans);
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
