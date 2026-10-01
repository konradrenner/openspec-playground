package org.kore.raumschiffwerft.service.boundary.integration;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.kore.raumschiffwerft.service.control.Zustellport;

/**
 * Boundary-Implementierung des Zustellports: ruft synchron die Camel-Route
 * direct:zustellen auf (choice nach Zielsystemtyp, siehe ZustellRoute) und
 * uebersetzt jeden technischen Misserfolg in ZustellungUngeklaert.
 */
@ApplicationScoped
public class CamelZustellport implements Zustellport {

    private final CamelContext camelContext;

    private ProducerTemplate producerTemplate;

    @Inject
    public CamelZustellport(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @PostConstruct
    void producerAufbauen() {
        this.producerTemplate = camelContext.createProducerTemplate();
    }

    @Override
    public Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag kaufauftrag,
                                         Zielsystemtyp zielsystemtyp) throws ZustellungUngeklaert {
        Exchange exchange = producerTemplate.send("direct:zustellen", ex -> {
            ex.getMessage().setHeader("zielsystemtyp", zielsystemtyp.name());
            ex.getMessage().setHeader("auftragsId", auftragsId);
            ex.getMessage().setBody(kaufauftrag);
        });

        Throwable fehler = exchange.getException();
        if (fehler != null) {
            throw new ZustellungUngeklaert("Zustellung ueber direct:zustellen gescheitert (zielsystem=%s)"
                    .formatted(zielsystemtyp), alsException(fehler));
        }
        return exchange.getMessage().getBody(Zustellbestaetigung.class);
    }

    private Exception alsException(Throwable fehler) {
        if (fehler instanceof Exception e) {
            return e;
        }
        return new IllegalStateException(fehler);
    }
}
