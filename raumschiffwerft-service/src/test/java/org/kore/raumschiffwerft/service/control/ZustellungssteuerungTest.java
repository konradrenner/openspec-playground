package org.kore.raumschiffwerft.service.control;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZustellungssteuerungTest {

    private final Zustellport zustellport = mock(Zustellport.class);
    private final ZustellungRepository zustellungRepository = mock(ZustellungRepository.class);
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    private final Zustellungssteuerung steuerung =
            new Zustellungssteuerung(zustellport, zustellungRepository, meterRegistry, 60);

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private Zustellung zustellung;

    @BeforeEach
    void aufbauen() throws SQLException {
        zustellung = new Zustellung(auftragsId, Zielsystemtyp.IMPERIUM,
                Zustellungsstatus.IN_ZUSTELLUNG, null, 0, null,
                OffsetDateTime.now().plusMinutes(10), "test-pod", OffsetDateTime.now());
        when(zustellungRepository.verbuchen(any(), any())).thenReturn(1);
    }

    private org.kore.raumschiffwerft.service.entity.Kaufauftrag auftrag(Zustellung zustellung) {
        return new org.kore.raumschiffwerft.service.entity.Kaufauftrag(auftragsId,
                new org.kore.raumschiffwerft.model.entity.Kaufauftrag("Tarkin",
                        Sternenzerstoererklasse.IMPERIAL_I, 2, 7),
                OffsetDateTime.now(), List.of(zustellung));
    }

    @Test
    void erfolgWirdAlsBestaetigtVerbucht() throws SQLException, ZustellungUngeklaert {
        when(zustellport.zustellen(auftragsId, auftrag(zustellung).kanonischerAuftrag(), Zielsystemtyp.IMPERIUM))
                .thenReturn(new Zustellbestaetigung("ISD-4711"));

        steuerung.zustellen(auftrag(zustellung));

        assertEquals(Zustellungsstatus.BESTAETIGT, zustellung.status());
        assertEquals("ISD-4711", zustellung.externeReferenz());
        verify(zustellungRepository).verbuchen(zustellung, Zustellungsstatus.IN_ZUSTELLUNG);
        assertEquals(1, meterRegistry.get("durchlauferhitzer.zustellungen")
                .tag("zielsystem", "IMPERIUM").tag("ergebnis", "BESTAETIGT").counter().count());
    }

    @Test
    void misserfolgWirdAlsUngeklaertMitNaechstemVersuchVerbucht() throws SQLException, ZustellungUngeklaert {
        when(zustellport.zustellen(any(), any(), any())).thenThrow(new ZustellungUngeklaert("Stub-Fehler"));

        steuerung.zustellen(auftrag(zustellung));

        assertEquals(Zustellungsstatus.UNGEKLAERT, zustellung.status());
        assertNotNull(zustellung.naechsterVersuchUm());
        verify(zustellungRepository).verbuchen(zustellung, Zustellungsstatus.IN_ZUSTELLUNG);
        assertEquals(1, meterRegistry.get("durchlauferhitzer.zustellungen")
                .tag("zielsystem", "IMPERIUM").tag("ergebnis", "UNGEKLAERT").counter().count());
    }

    @Test
    void verbuchenOhneTrefferWirftKeinenFehler() throws SQLException, ZustellungUngeklaert {
        when(zustellungRepository.verbuchen(any(), any())).thenReturn(0);
        when(zustellport.zustellen(any(), any(), any())).thenThrow(new ZustellungUngeklaert("Stub-Fehler"));

        steuerung.zustellen(auftrag(zustellung));

        verify(zustellungRepository).verbuchen(zustellung, Zustellungsstatus.IN_ZUSTELLUNG);
    }

    @Test
    void jedeZustellungWirdGenauEinmalAngestoßen() throws SQLException, ZustellungUngeklaert {
        when(zustellport.zustellen(any(), any(), any())).thenReturn(new Zustellbestaetigung("ISD-4711"));
        Zustellung zweite = new Zustellung(auftragsId, Zielsystemtyp.REBELLION,
                Zustellungsstatus.IN_ZUSTELLUNG, null, 0, null,
                OffsetDateTime.now().plusMinutes(10), "test-pod", OffsetDateTime.now());

        steuerung.zustellen(auftragMit(zustellung, zweite));

        verify(zustellport, org.mockito.Mockito.times(1)).zustellen(any(), any(),
                org.mockito.ArgumentMatchers.eq(Zielsystemtyp.IMPERIUM));
        verify(zustellport, org.mockito.Mockito.times(1)).zustellen(any(), any(),
                org.mockito.ArgumentMatchers.eq(Zielsystemtyp.REBELLION));
    }

    private org.kore.raumschiffwerft.service.entity.Kaufauftrag auftragMit(Zustellung erste, Zustellung zweite) {
        return new org.kore.raumschiffwerft.service.entity.Kaufauftrag(auftragsId,
                new org.kore.raumschiffwerft.model.entity.Kaufauftrag("Tarkin",
                        Sternenzerstoererklasse.IMPERIAL_I, 2, 7),
                OffsetDateTime.now(), List.of(erste, zweite));
    }
}
