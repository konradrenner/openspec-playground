package org.kore.raumschiffwerft.service.boundary.integration;

import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.adapter.imperium.boundary.ImperiumZielsystem;
import org.kore.raumschiffwerft.adapter.rebellion.boundary.RebellionZielsystem;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Test der Zustell-Route mit echtem Camel-Context: Der Body wird per
 * bean-validator geprueft, bevor der Adapter angewaehlt wird.
 */
class ZustellRouteTest {

    private final ImperiumZielsystem imperiumZielsystem = mock(ImperiumZielsystem.class);
    private final RebellionZielsystem rebellionZielsystem = mock(RebellionZielsystem.class);

    private CamelContext camelContext;
    private CamelZustellport zustellport;

    @BeforeEach
    void routeStarten() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new ZustellRoute(imperiumZielsystem, rebellionZielsystem));
        camelContext.start();
        zustellport = new CamelZustellport(camelContext);
        zustellport.producerAufbauen();
    }

    @AfterEach
    void routeStoppen() throws Exception {
        camelContext.stop();
    }

    @Test
    void verletzenderAuftragWirdNichtZugestellt() throws Exception {
        Kaufauftrag verletzender = new Kaufauftrag("", Sternenzerstoererklasse.VICTORY, 0, 1);

        assertThrows(ZustellungUngeklaert.class, () -> zustellport
                .zustellen(new AuftragsId(UUID.randomUUID()), verletzender, Zielsystemtyp.IMPERIUM));

        verify(imperiumZielsystem, never()).zustellen(any(), any());
        verify(rebellionZielsystem, never()).zustellen(any(), any());
    }

    @Test
    void gueltigerAuftragWirdZugestellt() throws Exception {
        when(imperiumZielsystem.zustellen(any(), any())).thenReturn(new Zustellbestaetigung("ISD-4711"));

        Zustellbestaetigung bestaetigung = zustellport.zustellen(
                new AuftragsId(UUID.randomUUID()),
                new Kaufauftrag("Mon Mothma", Sternenzerstoererklasse.VICTORY, 3, 42),
                Zielsystemtyp.IMPERIUM);

        assertEquals("ISD-4711", bestaetigung.externeReferenz());
        verify(imperiumZielsystem).zustellen(any(), any());
    }
}
