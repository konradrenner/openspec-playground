package org.kore.raumschiffwerft.service.control.journal;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.agroal.api.AgroalDataSource;

/**
 * Journal-Relay: versendet ungesendete Outbox-Zeilen in Batches
 * zuverlaessig nach Kafka (at-least-once). Der Durchlauf laeuft in einer
 * kurzen Transaktion: Zeilen mit FOR UPDATE SKIP LOCKED beanspruchen,
 * jede Zeile synchron senden (auf Bestaetigung warten), danach
 * gesendet_am setzen und committen. Ein Sendefehler rollt die
 * Transaktion zurueck - bereits gesendete Zeilen duerfen dann spaeter
 * erneut gesendet werden (Duplikate sind erlaubt). Ist Kafka nicht
 * erreichbar, bleibt die Outbox unberuehrt und der Rueckstand wird beim
 * naechsten Durchlauf nachgeholt; die Annahme ist davon unberuehrt.
 */
@ApplicationScoped
public class JournalRelay {

    private static final Logger LOG = Logger.getLogger(JournalRelay.class);

    private final AgroalDataSource dataSource;
    private final JournalOutboxRepository outboxRepository;
    private final JournalVersand versand;
    private final int batch;

    @Inject
    public JournalRelay(AgroalDataSource dataSource, JournalVersand versand,
                        @ConfigProperty(name = "journal.relay.batch") int batch) {
        this(dataSource, new JournalOutboxRepository(), versand, batch);
    }

    JournalRelay(AgroalDataSource dataSource, JournalOutboxRepository outboxRepository,
                 JournalVersand versand, int batch) {
        this.dataSource = dataSource;
        this.outboxRepository = outboxRepository;
        this.versand = versand;
        this.batch = batch;
    }

    /** Ein Relay-Durchlauf; Fehler werden geloggt, nicht geworfen. */
    public void relay() {
        try (Connection verbindung = dataSource.getConnection()) {
            verbindung.setAutoCommit(false);
            try {
                List<Journaleintrag> eintraege = outboxRepository.ungesendeteLesen(verbindung, batch);
                OffsetDateTime gesendetAm = OffsetDateTime.now();
                for (Journaleintrag eintrag : eintraege) {
                    versand.senden(eintrag.auftragsId(), eintrag.payload());
                    outboxRepository.gesendetMarkieren(verbindung, eintrag.id(), gesendetAm);
                }
                verbindung.commit();
                if (!eintraege.isEmpty()) {
                    LOG.infof("Journal-Relay: %d Eintraege versendet", eintraege.size());
                }
            } catch (SQLException | RuntimeException e) {
                try {
                    verbindung.rollback();
                } catch (SQLException rollbackFehler) {
                    e.addSuppressed(rollbackFehler);
                }
                LOG.warnf(e, "Journal-Relay: Durchlauf gescheitert, Rueckstand wird nachgeholt");
            }
        } catch (SQLException e) {
            LOG.warnf(e, "Journal-Relay: Verbindung zur Outbox nicht moeglich");
        }
    }
}
