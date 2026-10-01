package org.kore.raumschiffwerft.service.control;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

/**
 * Testdouble der OpenTelemetry-API fuer Metrik-Unit-Tests: zaehlt
 * Counter-Additionen (Name plus Attribute) und fuehrt Gauge-Callbacks
 * bei gaugesMessen() aus; Traces sind Noops. Kein SDK, keine Exporte -
 * bewusst frameworkfrei wie alle Unit-Tests.
 */
class MessendeTelemetrie implements OpenTelemetry {

    private final Map<String, Long> zaehler = new ConcurrentHashMap<>();
    private final Map<String, Long> gaugeWerte = new ConcurrentHashMap<>();
    private final Map<String, Consumer<ObservableDoubleMeasurement>> gaugeCallbacks =
            new ConcurrentHashMap<>();

    private final Meter meter = new Meter() {
        @Override
        public LongCounterBuilder counterBuilder(String name) {
            return new LongCounterBuilder() {
                @Override
                public LongCounterBuilder setDescription(String beschreibung) {
                    return this;
                }

                @Override
                public LongCounterBuilder setUnit(String einheit) {
                    return this;
                }

                @Override
                public io.opentelemetry.api.metrics.DoubleCounterBuilder ofDoubles() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public LongCounter build() {
                    return new LongCounter() {
                        @Override
                        public void add(long wert) {
                            add(wert, Attributes.empty());
                        }

                        @Override
                        public void add(long wert, Attributes attributes) {
                            zaehler.merge(name + attributes, wert, Long::sum);
                        }

                        @Override
                        public void add(long wert, Attributes attributes,
                                        io.opentelemetry.context.Context kontext) {
                            add(wert, attributes);
                        }
                    };
                }

                @Override
                public io.opentelemetry.api.metrics.ObservableLongCounter buildWithCallback(
                        Consumer<io.opentelemetry.api.metrics.ObservableLongMeasurement> callback) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public DoubleGaugeBuilder gaugeBuilder(String name) {
            return new DoubleGaugeBuilder() {
                @Override
                public DoubleGaugeBuilder setDescription(String beschreibung) {
                    return this;
                }

                @Override
                public DoubleGaugeBuilder setUnit(String einheit) {
                    return this;
                }

                @Override
                public io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public io.opentelemetry.api.metrics.ObservableDoubleGauge buildWithCallback(
                        Consumer<ObservableDoubleMeasurement> callback) {
                    gaugeCallbacks.put(name, callback);
                    return null;
                }
            };
        }

        @Override
        public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DoubleHistogramBuilder histogramBuilder(String name) {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    public TracerProvider getTracerProvider() {
        return TracerProvider.noop();
    }

    @Override
    public ContextPropagators getPropagators() {
        return ContextPropagators.noop();
    }

    @Override
    public MeterProvider getMeterProvider() {
        return new MeterProvider() {
            @Override
            public MeterBuilder meterBuilder(String instrumentationName) {
                return new MeterBuilder() {
                    @Override
                    public MeterBuilder setSchemaUrl(String schemaUrl) {
                        return this;
                    }

                    @Override
                    public MeterBuilder setInstrumentationVersion(String version) {
                        return this;
                    }

                    @Override
                    public Meter build() {
                        return meter;
                    }
                };
            }

            @Override
            public Meter get(String instrumentationName) {
                return meter;
            }
        };
    }

    /** Stand eines Counters fuer Name und Attribute; 0, wenn unberuehrt. */
    long stand(String name, Attributes attributes) {
        return zaehler.getOrDefault(name + attributes, 0L);
    }

    /** Summe aller Zaehlungen eines Counters unabhaengig von den Attributen. */
    long stand(String name) {
        return zaehler.entrySet().stream()
                .filter(eintrag -> eintrag.getKey().startsWith(name))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    /** Fuehrt alle Gauge-Callbacks aus und merkt sich deren Werte. */
    void gaugesMessen() {
        gaugeCallbacks.forEach((name, callback) -> callback.accept(
                new ObservableDoubleMeasurement() {
                    @Override
                    public void record(double wert) {
                        gaugeWerte.put(name, (long) wert);
                    }

                    @Override
                    public void record(double wert, Attributes attributes) {
                        gaugeWerte.put(name, (long) wert);
                    }
                }));
    }

    /** Letzter gemessener Wert eines Gauges; -1, wenn noch nicht gemessen. */
    long gaugeWert(String name) {
        return gaugeWerte.getOrDefault(name, -1L);
    }
}
