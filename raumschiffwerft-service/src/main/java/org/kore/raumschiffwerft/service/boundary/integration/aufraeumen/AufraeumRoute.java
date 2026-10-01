package org.kore.raumschiffwerft.service.boundary.integration.aufraeumen;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.service.control.aufraeumen.AufraeumSteuerung;

/**
 * Aufraeum-Route: der Timer triggert periodisch die Aufraeum-Steuerung
 * (konfigurierbares Intervall, Default 10 Minuten), die sich zuerst
 * per Advisory-Lock als einzige Instanz absichert.
 */
@ApplicationScoped
public class AufraeumRoute extends RouteBuilder {

    private final AufraeumSteuerung aufraeumSteuerung;
    private final long timerPeriodMillis;

    @Inject
    public AufraeumRoute(AufraeumSteuerung aufraeumSteuerung,
                         @ConfigProperty(name = "durchlauferhitzer.aufraeumen.timer-period-millis")
                         long timerPeriodMillis) {
        this.aufraeumSteuerung = aufraeumSteuerung;
        this.timerPeriodMillis = timerPeriodMillis;
    }

    @Override
    public void configure() {
        from("timer:aufraeumen?period=" + timerPeriodMillis).routeId("aufraeumen")
                .process(exchange -> aufraeumSteuerung.aufraeumen());
    }
}
