package org.kore.raumschiffwerft.service.boundary.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.adapter.imperium.boundary.ImperiumZielsystem;
import org.kore.raumschiffwerft.adapter.rebellion.boundary.RebellionZielsystem;

/**
 * Zustell-Route: direct:zustellen prueft den Body (kanonischer Kaufauftrag)
 * per Bean Validation und waehlt danach per choice nach dem Zielsystemtyp
 * den passenden Adapter aus. Header "auftragsId" (AuftragsId) und Body
 * (kanonischer Kaufauftrag) werden von der Zustellungssteuerung gesetzt;
 * der Body der Antwort ist die Zustellbestaetigung des Adapters.
 */
@ApplicationScoped
public class ZustellRoute extends RouteBuilder {

    private final ImperiumZielsystem imperiumZielsystem;
    private final RebellionZielsystem rebellionZielsystem;

    @Inject
    public ZustellRoute(ImperiumZielsystem imperiumZielsystem, RebellionZielsystem rebellionZielsystem) {
        this.imperiumZielsystem = imperiumZielsystem;
        this.rebellionZielsystem = rebellionZielsystem;
    }

    @Override
    public void configure() {
        from("direct:zustellen").routeId("zustellen")
                .to("bean-validator://kaufauftrag")
                .choice()
                .when(header("zielsystemtyp").isEqualTo(Zielsystemtyp.IMPERIUM.name()))
                .process(exchange -> {
                    AuftragsId auftragsId = exchange.getIn().getHeader("auftragsId", AuftragsId.class);
                    var kaufauftrag = exchange.getIn()
                            .getBody(org.kore.raumschiffwerft.model.entity.Kaufauftrag.class);
                    exchange.getIn().setBody(imperiumZielsystem.zustellen(auftragsId, kaufauftrag));
                })
                .when(header("zielsystemtyp").isEqualTo(Zielsystemtyp.REBELLION.name()))
                .process(exchange -> {
                    AuftragsId auftragsId = exchange.getIn().getHeader("auftragsId", AuftragsId.class);
                    var kaufauftrag = exchange.getIn()
                            .getBody(org.kore.raumschiffwerft.model.entity.Kaufauftrag.class);
                    exchange.getIn().setBody(rebellionZielsystem.zustellen(auftragsId, kaufauftrag));
                })
                .endChoice();
    }
}
