package org.kore.raumschiffwerft.service.control;

import java.time.Duration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

/**
 * Stellt die OTLP-MeterRegistry als CDI-Bean bereit; quarkus-micrometer
 * nimmt MeterRegistry-Beans automatisch in den Composite auf und verwaltet
 * ihren Lebenszyklus. Exportiert wird per OTLP HTTP an den Collector
 * (Default alle 10 s an localhost:4318).
 */
@ApplicationScoped
public class OtlpMetrikRegistry {

    @Produces
    @ApplicationScoped
    MeterRegistry otlpMeterRegistry(
            @ConfigProperty(name = "durchlauferhitzer.metrik.otlp.url") String url) {
        return new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public String get(String key) {
                return null; // alle uebrigen Konfigurationswerte bleiben Default
            }

            @Override
            public String url() {
                return url;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(10);
            }
        }, Clock.SYSTEM);
    }
}
