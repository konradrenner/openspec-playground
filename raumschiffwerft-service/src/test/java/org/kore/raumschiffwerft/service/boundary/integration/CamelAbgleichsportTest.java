package org.kore.raumschiffwerft.service.boundary.integration;

import java.util.UUID;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CamelAbgleichsportTest {

    private final CamelContext camelContext = mock(CamelContext.class);
    private final ProducerTemplate producerTemplate = mock(ProducerTemplate.class);
    private final CamelAbgleichsport abgleichsport = new CamelAbgleichsport(camelContext);

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());

    @BeforeEach
    void aufbauen() {
        when(camelContext.createProducerTemplate()).thenReturn(producerTemplate);
        abgleichsport.producerAufbauen();
    }

    private Exchange exchangeMit(Exception fehler, Object body) {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getException()).thenReturn(fehler);
        when(exchange.getMessage()).thenReturn(message);
        when(message.getBody(Verarbeitungsstatus.class)).thenReturn((Verarbeitungsstatus) body);
        return exchange;
    }

    @Test
    void erfolgLiefertDenStatusUndSetztHeader() throws Exception {
        Verarbeitungsstatus abgeschlossen =
                Verarbeitungsstatus.mitExternerReferenz(
                        Verarbeitungsstatus.Status.ABGESCHLOSSEN, "ISD-4711");
        Exchange erfolg = exchangeMit(null, abgeschlossen);
        when(producerTemplate.send(eq("direct:statusAbfragen"), any(org.apache.camel.Processor.class)))
                .thenReturn(erfolg);

        Verarbeitungsstatus status =
                abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM);

        assertEquals(Verarbeitungsstatus.Status.ABGESCHLOSSEN, status.status());
        assertEquals("ISD-4711", status.externeReferenz());
        ArgumentCaptor<org.apache.camel.Processor> captor =
                ArgumentCaptor.forClass(org.apache.camel.Processor.class);
        verify(producerTemplate).send(eq("direct:statusAbfragen"), captor.capture());
        Exchange ziel = mock(Exchange.class);
        Message nachricht = mock(Message.class);
        when(ziel.getMessage()).thenReturn(nachricht);
        captor.getValue().process(ziel);
        verify(nachricht).setHeader("zielsystemtyp", Zielsystemtyp.IMPERIUM.name());
        verify(nachricht).setHeader("auftragsId", auftragsId);
    }

    @Test
    void fehlerAufDemExchangeWirdZuZustellungUngeklaert() {
        Exchange misserfolg = exchangeMit(new ZustellungUngeklaert("Stub-Fehler"), null);
        when(producerTemplate.send(eq("direct:statusAbfragen"), any(org.apache.camel.Processor.class)))
                .thenReturn(misserfolg);

        assertThrows(ZustellungUngeklaert.class,
                () -> abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.REBELLION));
    }

    @Test
    void unerwarteterFehlerWirdEbenfallsZuZustellungUngeklaert() {
        Exchange unerwartet = exchangeMit(new RuntimeException("unerwartet"), null);
        when(producerTemplate.send(eq("direct:statusAbfragen"), any(org.apache.camel.Processor.class)))
                .thenReturn(unerwartet);

        assertThrows(ZustellungUngeklaert.class,
                () -> abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM));
    }
}
