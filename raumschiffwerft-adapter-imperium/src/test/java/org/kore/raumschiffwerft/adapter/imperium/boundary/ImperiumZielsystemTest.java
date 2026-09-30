package org.kore.raumschiffwerft.adapter.imperium.boundary;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.adapter.imperium.control.ImperiumUebersetzer;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.AbfrageBestellstatusResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoerer;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.BestelleSternenzerstoererResponse;
import org.kore.raumschiffwerft.adapter.imperium.werft.v1.ImperiumWerft;
import org.kore.raumschiffwerft.model.boundary.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImperiumZielsystemTest {

    private final ImperiumWerft werft = mock(ImperiumWerft.class);
    private final ImperiumZielsystem zielsystem = new ImperiumZielsystem(werft, new ImperiumUebersetzer());

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final Kaufauftrag auftrag = new Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_II, 2, 3);

    @Test
    void typIstImperium() {
        assertEquals(Zielsystemtyp.IMPERIUM, zielsystem.typ());
    }

    @Test
    void zustellungLiefertBestellnummer() throws ZustellungUngeklaert {
        BestelleSternenzerstoererResponse antwort = new BestelleSternenzerstoererResponse();
        antwort.setBestellnummer("ISD-4711");
        when(werft.bestelleSternenzerstoerer(any(BestelleSternenzerstoerer.class))).thenReturn(antwort);

        assertEquals("ISD-4711", zielsystem.zustellen(auftragsId, auftrag).externeReferenz());
    }

    @Test
    void soapFaultWirdZuZustellungUngeklaert() throws ZustellungUngeklaert {
        when(werft.bestelleSternenzerstoerer(any(BestelleSternenzerstoerer.class)))
                .thenThrow(new RuntimeException("SOAP-Fault"));

        assertThrows(ZustellungUngeklaert.class, () -> zielsystem.zustellen(auftragsId, auftrag));
        verify(werft, times(1)).bestelleSternenzerstoerer(any());
    }

    @Test
    void statusabfrageLiefertKanonischenStatus() throws ZustellungUngeklaert {
        AbfrageBestellstatusResponse antwort = new AbfrageBestellstatusResponse();
        antwort.setStatus("IN_BEARBEITUNG");
        antwort.setBestellnummer("ISD-4711");
        when(werft.abfrageBestellstatus(any())).thenReturn(antwort);

        Verarbeitungsstatus status = zielsystem.statusAbfragen(auftragsId);

        assertEquals(Verarbeitungsstatus.Status.IN_BEARBEITUNG, status.status());
        assertEquals("ISD-4711", status.externeReferenz());
    }

    @Test
    void fehlerBeiDerStatusabfrageWirdZuZustellungUngeklaert() {
        when(werft.abfrageBestellstatus(any())).thenThrow(new RuntimeException("Timeout"));

        assertThrows(ZustellungUngeklaert.class, () -> zielsystem.statusAbfragen(auftragsId));
        verify(werft, times(1)).abfrageBestellstatus(any());
    }
}
