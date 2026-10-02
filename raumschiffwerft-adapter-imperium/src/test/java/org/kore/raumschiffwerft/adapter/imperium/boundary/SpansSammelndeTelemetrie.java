package org.kore.raumschiffwerft.adapter.imperium.boundary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;

/**
 * Testdouble der OpenTelemetry-API fuer Span-Unit-Tests: sammelt beendete
 * Spans mit Name, Kind, Attributen, Status und aufgenommenen Ausnahmen.
 * Kein SDK, keine Exporte - bewusst frameworkfrei wie alle Unit-Tests.
 */
public class SpansSammelndeTelemetrie implements OpenTelemetry {

    private final List<GesammelterSpan> spans = new ArrayList<>();

    /** Ein gesammelter Span mit allem, was der Test pruefen will. */
    public record GesammelterSpan(String name, SpanKind kind, Map<String, Object> attribute,
                                  StatusCode status, List<Throwable> ausnahmen) {
    }

    /** Alle beendeten Spans in Beendigungsreihenfolge. */
    public List<GesammelterSpan> spans() {
        return List.copyOf(spans);
    }

    /** Erster Span mit dem Namen; null, wenn es keinen gibt. */
    public GesammelterSpan span(String name) {
        return spans.stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
    }

    @Override
    public TracerProvider getTracerProvider() {
        return new TracerProvider() {
            @Override
            public Tracer get(String instrumentationName) {
                return tracer();
            }

            @Override
            public Tracer get(String instrumentationName, String instrumentationVersion) {
                return tracer();
            }
        };
    }

    @Override
    public ContextPropagators getPropagators() {
        return ContextPropagators.noop();
    }

    private Tracer tracer() {
        return new Tracer() {
            @Override
            public SpanBuilder spanBuilder(String spanName) {
                return new SammelnderSpanBuilder(spanName);
            }
        };
    }

    private final class SammelnderSpanBuilder implements SpanBuilder {

        private final String name;
        private SpanKind kind = SpanKind.INTERNAL;
        private final Map<String, Object> attribute = new LinkedHashMap<>();

        private SammelnderSpanBuilder(String name) {
            this.name = name;
        }

        @Override
        public SpanBuilder setParent(io.opentelemetry.context.Context context) {
            return this;
        }

        @Override
        public SpanBuilder setNoParent() {
            return this;
        }

        @Override
        public SpanBuilder addLink(SpanContext spanContext) {
            return this;
        }

        @Override
        public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, String value) {
            attribute.put(key, value);
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, long value) {
            attribute.put(key, value);
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, double value) {
            attribute.put(key, value);
            return this;
        }

        @Override
        public SpanBuilder setAttribute(String key, boolean value) {
            attribute.put(key, value);
            return this;
        }

        @Override
        public <T> SpanBuilder setAttribute(AttributeKey<T> key, T value) {
            attribute.put(key.getKey(), value);
            return this;
        }

        @Override
        public SpanBuilder setSpanKind(SpanKind spanKind) {
            this.kind = spanKind;
            return this;
        }

        @Override
        public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
            return this;
        }

        @Override
        public Span startSpan() {
            return new SammelnderSpan(name, kind, attribute);
        }
    }

    private final class SammelnderSpan implements Span {

        private final String name;
        private final SpanKind kind;
        private final Map<String, Object> attribute;
        private StatusCode status = StatusCode.UNSET;
        private final List<Throwable> ausnahmen = new ArrayList<>();

        private SammelnderSpan(String name, SpanKind kind, Map<String, Object> attribute) {
            this.name = name;
            this.kind = kind;
            this.attribute = attribute;
        }

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value) {
            attribute.put(key.getKey(), value);
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes) {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
            return this;
        }

        @Override
        public Span setStatus(StatusCode statusCode, String description) {
            this.status = statusCode;
            return this;
        }

        @Override
        public Span recordException(Throwable exception, Attributes attributes) {
            ausnahmen.add(exception);
            return this;
        }

        @Override
        public Span updateName(String name) {
            return this;
        }

        @Override
        public void end() {
            spans.add(new GesammelterSpan(name, kind, Map.copyOf(attribute), status,
                    List.copyOf(ausnahmen)));
        }

        @Override
        public void end(long timestamp, TimeUnit unit) {
            end();
        }

        @Override
        public SpanContext getSpanContext() {
            return SpanContext.getInvalid();
        }

        @Override
        public boolean isRecording() {
            return true;
        }

        @Override
        public Scope makeCurrent() {
            return Scope.noop();
        }
    }
}
