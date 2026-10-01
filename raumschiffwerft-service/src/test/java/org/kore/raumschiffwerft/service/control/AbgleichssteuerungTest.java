package org.kore.raumschiffwerft.service.control;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbgleichssteuerungTest {

    private final ZustellungRepository zustellungRepository = mock(ZustellungRepository.class);
    private final Abgleichsport abgleichsport = mock(Abgleichsport.class);
    private final Zustellport zustellport = mock(Zustellport.class);
    private final MessendeTelemetrie telemetrie = new MessendeTelemetrie();

    // kurze Backoff-Basis und max-versuche 2 wie im Testprofil
    private final Abgleichssteuerung steuerung =
            new Abgleichssteuerung(zustellungRepository, abgleichsport, zustellport,
                    telemetrie, 1, 4, 0.2, 2, 20, 2, "abgleich-pod");

    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final Kaufauftrag kaufauftrag =
            new Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_I, 2, 7);

    /** Baut eine beanspruchte Zustellung: IN_ABGLEICH mit dem Versuchszaehler nach dem Beanspruchen. */
    private Beanspruchung beansprucht(int versuche) {
        return beansprucht(versuche, Zustellungsstatus.UNGEKLAERT);
    }

    private Beanspruchung beansprucht(int versuche, Zustellungsstatus ausgangsstatus) {
        return new Beanspruchung(
                new Zustellung(auftragsId, Zielsystemtyp.IMPERIUM, Zustellungsstatus.IN_ABGLEICH,
                        null, versuche, null, OffsetDateTime.now().plusSeconds(30), "abgleich-pod",
                        OffsetDateTime.now()),
                ausgangsstatus,
                kaufauftrag);
    }

    private void durchlaufMit(Beanspruchung beanspruchung) {
        when(zustellungRepository.faelligeBeanspruchen(anyInt(), any(), anyString()))
                .thenReturn(List.of(beanspruchung));
        steuerung.abgleichen();
    }

    @Test
    void abgeschlossenWirdBestaetigtVerbucht() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.mitExternerReferenz(
                        Verarbeitungsstatus.Status.ABGESCHLOSSEN, "ISD-4711"));

        durchlaufMit(beanspruchung);

        assertEquals(Zustellungsstatus.BESTAETIGT, beanspruchung.zustellung().status());
        assertEquals("ISD-4711", beanspruchung.zustellung().externeReferenz());
        verify(zustellungRepository).verbuchen(beanspruchung.zustellung(), Zustellungsstatus.IN_ABGLEICH);
        verify(zustellport, never()).zustellen(any(), any(), any());
    }

    @Test
    void abgeschlossenOhneReferenzBleibtOffen() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.von(Verarbeitungsstatus.Status.ABGESCHLOSSEN));

        durchlaufMit(beanspruchung);

        assertEquals(Zustellungsstatus.UNGEKLAERT, beanspruchung.zustellung().status());
        assertNotNull(beanspruchung.zustellung().naechsterVersuchUm());
    }

    @Test
    void inBearbeitungWirdUngeklaertMitBackoffVerbucht() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.von(Verarbeitungsstatus.Status.IN_BEARBEITUNG));

        durchlaufMit(beanspruchung);

        assertEquals(Zustellungsstatus.UNGEKLAERT, beanspruchung.zustellung().status());
        assertNotNull(beanspruchung.zustellung().naechsterVersuchUm());
        assertNull(beanspruchung.zustellung().externeReferenz());
        verify(zustellport, never()).zustellen(any(), any(), any());
    }

    @Test
    void abfragefehlerWirdUngeklaertVerbucht() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenThrow(new ZustellungUngeklaert("Statusabfrage gescheitert"));

        durchlaufMit(beanspruchung);

        assertEquals(Zustellungsstatus.UNGEKLAERT, beanspruchung.zustellung().status());
        assertNotNull(beanspruchung.zustellung().naechsterVersuchUm());
    }

    @Test
    void unbekanntLoestNeuversandAusUndBestaetigt() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.von(Verarbeitungsstatus.Status.UNBEKANNT));
        when(zustellport.zustellen(auftragsId, kaufauftrag, Zielsystemtyp.IMPERIUM))
                .thenReturn(new Zustellbestaetigung("ISD-4711"));

        durchlaufMit(beanspruchung);

        assertEquals(Zustellungsstatus.BESTAETIGT, beanspruchung.zustellung().status());
        assertEquals("ISD-4711", beanspruchung.zustellung().externeReferenz());
        verify(zustellport).zustellen(auftragsId, kaufauftrag, Zielsystemtyp.IMPERIUM);
    }

    @Test
    void gescheiterterNeuversandWirdUngeklaertVerbucht() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.von(Verarbeitungsstatus.Status.UNBEKANNT));
        when(zustellport.zustellen(auftragsId, kaufauftrag, Zielsystemtyp.IMPERIUM))
                .thenThrow(new ZustellungUngeklaert("Neuversand gescheitert"));

        durchlaufMit(beanspruchung);

        assertEquals(Zustellungsstatus.UNGEKLAERT, beanspruchung.zustellung().status());
        assertNotNull(beanspruchung.zustellung().naechsterVersuchUm());
    }

    @Test
    void maxVersucheMachenDieZustellungEndgueltig() throws Exception {
        Beanspruchung beanspruchung = beansprucht(2);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.von(Verarbeitungsstatus.Status.IN_BEARBEITUNG));

        durchlaufMit(beanspruchung);

        assertEquals(Zustellungsstatus.FEHLGESCHLAGEN, beanspruchung.zustellung().status());
        assertNull(beanspruchung.zustellung().naechsterVersuchUm());
    }

    @Test
    void ergebnisZaehltDenZustellCounter() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.mitExternerReferenz(
                        Verarbeitungsstatus.Status.ABGESCHLOSSEN, "ISD-4711"));

        durchlaufMit(beanspruchung);

        assertEquals(1, telemetrie.stand("durchlauferhifter.zustellungen",
                Attributes.of(AttributeKey.stringKey("zielsystem"), "IMPERIUM",
                        AttributeKey.stringKey("ergebnis"), "BESTAETIGT")));
    }

    @Test
    void leaseUebernahmeZaehltDenLeaseCounter() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1, Zustellungsstatus.IN_ZUSTELLUNG);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.mitExternerReferenz(
                        Verarbeitungsstatus.Status.ABGESCHLOSSEN, "ISD-4711"));

        durchlaufMit(beanspruchung);

        assertEquals(1, telemetrie.stand("durchlauferhifter.lease.abgelaufen",
                Attributes.of(AttributeKey.stringKey("ausgangsstatus"), "IN_ZUSTELLUNG")));
    }

    @Test
    void faelligeUngEKlaerteZaehlenKeinenLeaseCounter() throws Exception {
        Beanspruchung beanspruchung = beansprucht(1, Zustellungsstatus.UNGEKLAERT);
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.mitExternerReferenz(
                        Verarbeitungsstatus.Status.ABGESCHLOSSEN, "ISD-4711"));

        durchlaufMit(beanspruchung);

        assertEquals(0, telemetrie.stand("durchlauferhifter.lease.abgelaufen"));
    }

    @Test
    void fehlerEinerZeileBrichtDenDurchlaufNichtAb() throws Exception {
        Beanspruchung gescheitert = beansprucht(1);
        AuftragsId zweiteId = new AuftragsId(UUID.randomUUID());
        Beanspruchung erfolgreich = new Beanspruchung(
                new Zustellung(zweiteId, Zielsystemtyp.IMPERIUM, Zustellungsstatus.IN_ABGLEICH,
                        null, 1, null, OffsetDateTime.now().plusSeconds(30), "abgleich-pod",
                        OffsetDateTime.now()),
                Zustellungsstatus.UNGEKLAERT,
                kaufauftrag);
        when(zustellungRepository.faelligeBeanspruchen(anyInt(), any(), anyString()))
                .thenReturn(List.of(gescheitert, erfolgreich));
        when(abgleichsport.statusAbfragen(auftragsId, Zielsystemtyp.IMPERIUM))
                .thenThrow(new RuntimeException("unerwartet"));
        when(abgleichsport.statusAbfragen(zweiteId, Zielsystemtyp.IMPERIUM))
                .thenReturn(Verarbeitungsstatus.mitExternerReferenz(
                        Verarbeitungsstatus.Status.ABGESCHLOSSEN, "ISD-4711"));

        steuerung.abgleichen();

        assertEquals(Zustellungsstatus.BESTAETIGT, erfolgreich.zustellung().status());
        verify(zustellungRepository).verbuchen(erfolgreich.zustellung(),
                Zustellungsstatus.IN_ABGLEICH);
    }

    @Test
    void beanspruchenFehlerBeendetDenDurchlaufOhneException() throws Exception {
        when(zustellungRepository.faelligeBeanspruchen(anyInt(), any(), anyString()))
                .thenThrow(new IllegalStateException("Datenbank weg"));

        steuerung.abgleichen();

        verify(abgleichsport, never()).statusAbfragen(any(), any());
    }
}
