package org.kore.raumschiffwerft.service.control.aufraeumen;

import java.util.List;
import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Aufraeum-Steuerung: ein Durchlauf sichert sich zuerst mit der
 * Advisory-Sperre ab (bricht ab, wenn ein anderer Pod aufraeumt),
 * loescht dann gesendete Journal-Zeilen und danach die Auftraege, die
 * die Aufbewahrungsregel freigibt. Fehler brechen den Durchlauf ab,
 * ohne die Sperre offen zu lassen; sie werden geloggt, nicht geworfen.
 */
@ApplicationScoped
public class AufraeumSteuerung {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(AufraeumSteuerung.class.getName());

    private final Aufraeumung aufraeumung;
    private final Aufbewahrungsregel regel;
    private final int batch;

    @Inject
    public AufraeumSteuerung(Aufraeumung aufraeumung, Aufbewahrungsregel regel,
                             @ConfigProperty(name = "durchlauferhitzer.aufraeumen.batch") int batch) {
        this.aufraeumung = aufraeumung;
        this.regel = regel;
        this.batch = batch;
    }

    /** Ein Aufraeum-Durchlauf; niemals werfend. */
    public void aufraeumen() {
        boolean gesperrt = false;
        try {
            gesperrt = aufraeumung.sperren();
            if (!gesperrt) {
                return; // ein anderer Pod raeumt bereits auf
            }
            aufraeumung.gesendeteJournalZeilenLoeschen(batch);

            var jetzt = java.time.OffsetDateTime.now();
            List<UUID> loeschbar = aufraeumung.kandidaten(regel.stichtag(jetzt), batch).stream()
                    .filter(kandidat -> regel.loeschbar(kandidat.zustaende(),
                            kandidat.angenommenAm(), jetzt))
                    .map(AuftragKandidat::auftragsId)
                    .toList();
            if (!loeschbar.isEmpty()) {
                aufraeumung.auftraegeLoeschen(loeschbar);
                LOG.log(java.util.logging.Level.INFO,
                        "Aufraeumen: {0} abgeschlossene Auftraege geloescht", loeschbar.size());
            }
        } catch (RuntimeException e) {
            LOG.log(java.util.logging.Level.WARNING, "Aufraeumen: Durchlauf gescheitert", e);
        } finally {
            if (gesperrt) {
                aufraeumung.entsperren();
            }
        }
    }
}
