package org.kore.raumschiffwerft.service.control;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;

/**
 * Betriebs-Metriken der Raumschiffwerft als Gauges (ausschliesslich
 * OpenTelemetry API): offene und fehlgeschlagene Zustellungen sowie der
 * ungesendete Outbox-Rueckstand. Gezaehlt wird bei Abfrage ueber den
 * Bestandszaehler (kein Poll-Thread).
 */
@ApplicationScoped
public class Betriebsmetriken {

    private final Meter meter;
    private final Bestandszaehler bestandszaehler;

    @Inject
    public Betriebsmetriken(OpenTelemetry openTelemetry, Bestandszaehler bestandszaehler) {
        this.meter = openTelemetry.getMeterProvider().get("durchlauferhifter");
        this.bestandszaehler = bestandszaehler;
    }

    /** Registriert die Gauges beim Start (die Bean wird dadurch erzeugt). */
    void gaugesRegistrieren(@Observes StartupEvent startup) {
        meter.gaugeBuilder("durchlauferhifter.zustellungen.offen")
                .buildWithCallback(messung -> messung.record(bestandszaehler.offeneZustellungen()));
        meter.gaugeBuilder("durchlauferhifter.zustellungen.fehlgeschlagen")
                .buildWithCallback(messung -> messung.record(bestandszaehler.fehlgeschlageneZustellungen()));
        meter.gaugeBuilder("durchlauferhifter.outbox.rueckstand")
                .buildWithCallback(messung -> messung.record(bestandszaehler.outboxRueckstand()));
    }
}
