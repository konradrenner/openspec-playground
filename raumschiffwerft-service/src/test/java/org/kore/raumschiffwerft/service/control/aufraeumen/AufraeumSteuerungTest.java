package org.kore.raumschiffwerft.service.control.aufraeumen;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AufraeumSteuerungTest {

    private final Aufraeumung aufraeumung = mock(Aufraeumung.class);
    private final Aufbewahrungsregel regel = new Aufbewahrungsregel(Duration.ofDays(7));
    private final AufraeumSteuerung steuerung = new AufraeumSteuerung(aufraeumung, regel, 1000);

    private final UUID loeschbarId = UUID.randomUUID();
    private final UUID frischId = UUID.randomUUID();
    private final UUID fehlgeschlagenId = UUID.randomUUID();

    private AuftragKandidat kandidat(UUID id, OffsetDateTime angenommenAm,
                                    Zustellungsstatus... zustaende) {
        return new AuftragKandidat(id, angenommenAm, List.of(zustaende));
    }

    @Test
    void freieSperreRaeumtJournalUndAbgeschlosseneAuftraegeWeg() {
        when(aufraeumung.sperren()).thenReturn(true);
        when(aufraeumung.kandidaten(any(), eq(1000))).thenReturn(List.of(
                kandidat(loeschbarId, OffsetDateTime.now().minusDays(30),
                        Zustellungsstatus.BESTAETIGT),
                kandidat(frischId, OffsetDateTime.now(),
                        Zustellungsstatus.BESTAETIGT),
                kandidat(fehlgeschlagenId, OffsetDateTime.now().minusDays(30),
                        Zustellungsstatus.BESTAETIGT, Zustellungsstatus.FEHLGESCHLAGEN)));

        steuerung.aufraeumen();

        verify(aufraeumung).gesendeteJournalZeilenLoeschen(1000);
        // nur der abgeschlossene, abgelaufene Kandidat wird geloescht
        verify(aufraeumung).auftraegeLoeschen(List.of(loeschbarId));
        verify(aufraeumung).entsperren();
    }

    @Test
    void belegteSperreBrichtOhneJedeLoeschungAb() {
        when(aufraeumung.sperren()).thenReturn(false);

        steuerung.aufraeumen();

        verify(aufraeumung, never()).gesendeteJournalZeilenLoeschen(anyInt());
        verify(aufraeumung, never()).kandidaten(any(), anyInt());
        verify(aufraeumung, never()).auftraegeLoeschen(any());
        verify(aufraeumung, never()).entsperren();
    }

    @Test
    void leereKandidatenlisteLoeschtKeineAuftraege() {
        when(aufraeumung.sperren()).thenReturn(true);
        when(aufraeumung.kandidaten(any(), anyInt())).thenReturn(List.of());

        steuerung.aufraeumen();

        verify(aufraeumung, never()).auftraegeLoeschen(any());
        verify(aufraeumung).entsperren();
    }

    @Test
    void fehlerWaehrendDesDurchlaufsEntsperrtTrotzdem() {
        when(aufraeumung.sperren()).thenReturn(true);
        when(aufraeumung.kandidaten(any(), anyInt()))
                .thenThrow(new IllegalStateException("Datenbank weg"));

        steuerung.aufraeumen();

        verify(aufraeumung).entsperren();
    }

    @Test
    void sperreWirftOhneFreigabeUndOhneAbsturz() {
        when(aufraeumung.sperren()).thenThrow(new IllegalStateException("keine Verbindung"));

        steuerung.aufraeumen();

        verify(aufraeumung, never()).gesendeteJournalZeilenLoeschen(anyInt());
        verify(aufraeumung, never()).entsperren();
    }
}
