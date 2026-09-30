package org.kore.raumschiffwerft.service.boundary;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.service.control.Abgleichsport;

/**
 * Boundary-Implementierung des Abgleichsports: ruft synchron die
 * Camel-Route direct:statusAbfragen auf (choice nach Zielsystemtyp,
 * siehe AbgleichRoute) und uebersetzt jeden technischen Misserfolg in
 * ZustellungUngeklaert.
 */
@ApplicationScoped
public class CamelAbgleichsport implements Abgleichsport {

    private final CamelContext camelContext;

    private ProducerTemplate producerTemplate;

    @Inject
    public CamelAbgleichsport(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @PostConstruct
    void producerAufbauen() {
        this.producerTemplate = camelContext.createProducerTemplate();
    }

    @Override
    public Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId, Zielsystemtyp zielsystemtyp)
            throws ZustellungUngeklaert {
        Exchange exchange = producerTemplate.send("direct:statusAbfragen", ex -> {
            ex.getMessage().setHeader("zielsystemtyp", zielsystemtyp.name());
            ex.getMessage().setHeader("auftragsId", auftragsId);
        });

        Throwable fehler = exchange.getException();
        if (fehler != null) {
            throw new ZustellungUngeklaert("Statusabfrage ueber direct:statusAbfragen gescheitert (zielsystem=%s)"
                    .formatted(zielsystemtyp), alsException(fehler));
        }
        return exchange.getMessage().getBody(Verarbeitungsstatus.class);
    }

    private Exception alsException(Throwable fehler) {
        if (fehler instanceof Exception e) {
            return e;
        }
        return new IllegalStateException(fehler);
    }
}
