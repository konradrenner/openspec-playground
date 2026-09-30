package org.kore.raumschiffwerft.service.boundary;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.adapter.imperium.boundary.ImperiumZielsystem;
import org.kore.raumschiffwerft.adapter.rebellion.boundary.RebellionZielsystem;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.service.control.Abgleichssteuerung;

/**
 * Abgleich-Route des Services: der Timer triggert periodisch die
 * Abgleichssteuerung, die faellige Zustellungen atomar beansprucht und
 * vor jedem Neuversand zuerst den Status am Zielsystem abfragt.
 * direct:statusAbfragen waehlt per choice nach dem Zielsystemtyp den
 * passenden Adapter aus (Header "auftragsId", Body der Antwort ist der
 * Verarbeitungsstatus).
 */
@ApplicationScoped
public class AbgleichRoute extends RouteBuilder {

    private final Abgleichssteuerung abgleichssteuerung;
    private final ImperiumZielsystem imperiumZielsystem;
    private final RebellionZielsystem rebellionZielsystem;
    private final long timerPeriodMillis;

    @Inject
    public AbgleichRoute(Abgleichssteuerung abgleichssteuerung,
                         ImperiumZielsystem imperiumZielsystem,
                         RebellionZielsystem rebellionZielsystem,
                         @ConfigProperty(name = "abgleich.timer-period-millis")
                         long timerPeriodMillis) {
        this.abgleichssteuerung = abgleichssteuerung;
        this.imperiumZielsystem = imperiumZielsystem;
        this.rebellionZielsystem = rebellionZielsystem;
        this.timerPeriodMillis = timerPeriodMillis;
    }

    @Override
    public void configure() {
        from("timer:abgleich?period=" + timerPeriodMillis).routeId("abgleich")
                .process(exchange -> abgleichssteuerung.abgleichen());

        from("direct:statusAbfragen").routeId("statusAbfragen")
                .choice()
                .when(header("zielsystemtyp").isEqualTo(Zielsystemtyp.IMPERIUM.name()))
                .process(exchange -> {
                    AuftragsId auftragsId = exchange.getIn().getHeader("auftragsId", AuftragsId.class);
                    exchange.getIn().setBody(imperiumZielsystem.statusAbfragen(auftragsId));
                })
                .when(header("zielsystemtyp").isEqualTo(Zielsystemtyp.REBELLION.name()))
                .process(exchange -> {
                    AuftragsId auftragsId = exchange.getIn().getHeader("auftragsId", AuftragsId.class);
                    exchange.getIn().setBody(rebellionZielsystem.statusAbfragen(auftragsId));
                })
                .endChoice();
    }
}
