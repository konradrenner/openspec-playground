package org.kore.raumschiffwerft.service.boundary.integration;

import java.util.UUID;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CamelZustellportTest {

    private final CamelContext camelContext = mock(CamelContext.class);
    private final ProducerTemplate producerTemplate = mock(ProducerTemplate.class);
    private final CamelZustellport zustellport = new CamelZustellport(camelContext);

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final org.kore.raumschiffwerft.model.entity.Kaufauftrag kaufauftrag =
            new org.kore.raumschiffwerft.model.entity.Kaufauftrag("Tarkin",
                    Sternenzerstoererklasse.IMPERIAL_I, 2, 7);

    @BeforeEach
    void aufbauen() {
        when(camelContext.createProducerTemplate()).thenReturn(producerTemplate);
        zustellport.producerAufbauen();
    }

    private Exchange exchangeMit(Exception fehler, Object body) {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getException()).thenReturn(fehler);
        when(exchange.getMessage()).thenReturn(message);
        when(message.getBody(Zustellbestaetigung.class)).thenReturn((Zustellbestaetigung) body);
        return exchange;
    }

    @Test
    void erfolgLiefertDieBestaetigungUndSetztHeader() throws Exception {
        Exchange erfolg = exchangeMit(null, new Zustellbestaetigung("ISD-4711"));
        when(producerTemplate.send(eq("direct:zustellen"), any(org.apache.camel.Processor.class)))
                .thenReturn(erfolg);

        Zustellbestaetigung bestaetigung =
                zustellport.zustellen(auftragsId, kaufauftrag, Zielsystemtyp.IMPERIUM);

        assertEquals("ISD-4711", bestaetigung.externeReferenz());
        ArgumentCaptor<org.apache.camel.Processor> captor =
                ArgumentCaptor.forClass(org.apache.camel.Processor.class);
        verify(producerTemplate).send(eq("direct:zustellen"), captor.capture());
        Exchange ziel = mock(Exchange.class);
        Message nachricht = mock(Message.class);
        when(ziel.getMessage()).thenReturn(nachricht);
        captor.getValue().process(ziel);
        verify(nachricht).setHeader("zielsystemtyp", Zielsystemtyp.IMPERIUM.name());
        verify(nachricht).setHeader("auftragsId", auftragsId);
        verify(nachricht).setBody(kaufauftrag);
    }

    @Test
    void fehlerAufDemExchangeWirdZuZustellungUngeklaert() {
        Exchange misserfolg = exchangeMit(new ZustellungUngeklaert("Stub-Fehler"), null);
        when(producerTemplate.send(eq("direct:zustellen"), any(org.apache.camel.Processor.class)))
                .thenReturn(misserfolg);

        assertThrows(ZustellungUngeklaert.class,
                () -> zustellport.zustellen(auftragsId, kaufauftrag, Zielsystemtyp.REBELLION));
    }

    @Test
    void unerwarteterFehlerWirdEbenfallsZuZustellungUngeklaert() {
        Exchange unerwartet = exchangeMit(new RuntimeException("unerwartet"), null);
        when(producerTemplate.send(eq("direct:zustellen"), any(org.apache.camel.Processor.class)))
                .thenReturn(unerwartet);

        assertThrows(ZustellungUngeklaert.class,
                () -> zustellport.zustellen(auftragsId, kaufauftrag, Zielsystemtyp.IMPERIUM));
    }
}
