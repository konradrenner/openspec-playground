package org.kore.raumschiffwerft.service.control;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;

/**
 * Betriebs-Metriken der Raumschiffwerft als Gauges: offene und
 * fehlgeschlagene Zustellungen sowie der ungesendete Outbox-Rueckstand.
 * Gezaehlt wird bei Abfrage (kein Poll-Thread).
 */
@ApplicationScoped
public class Betriebsmetriken {

    private static final Logger LOG = Logger.getLogger(Betriebsmetriken.class);

    private static final String OFFENE_ZUSTELLUNGEN =
            "SELECT count(*) FROM zustellung "
                    + "WHERE status IN ('IN_ZUSTELLUNG', 'IN_ABGLEICH', 'UNGEKLAERT')";
    private static final String FEHLGESCHLAGENE_ZUSTELLUNGEN =
            "SELECT count(*) FROM zustellung WHERE status = 'FEHLGESCHLAGEN'";
    private static final String OUTBOX_RUECKSTAND =
            "SELECT count(*) FROM journal_outbox WHERE gesendet_am IS NULL";

    private final MeterRegistry meterRegistry;
    private final AgroalDataSource dataSource;

    @Inject
    public Betriebsmetriken(MeterRegistry meterRegistry, AgroalDataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
    }

    /** Registriert die Gauges beim Start (die Bean wird dadurch erzeugt). */
    void gaugesRegistrieren(@Observes StartupEvent startup) {
        Gauge.builder("durchlauferhitzer.zustellungen.offen", this, m -> m.zaehlen(OFFENE_ZUSTELLUNGEN))
                .register(meterRegistry);
        Gauge.builder("durchlauferhitzer.zustellungen.fehlgeschlagen", this,
                        m -> m.zaehlen(FEHLGESCHLAGENE_ZUSTELLUNGEN))
                .register(meterRegistry);
        Gauge.builder("durchlauferhitzer.outbox.rueckstand", this,
                        m -> m.zaehlen(OUTBOX_RUECKSTAND))
                .register(meterRegistry);
    }

    long zaehlen(String sql) {
        try (Connection verbindung = dataSource.getConnection();
                PreparedStatement ps = verbindung.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            LOG.warnf(e, "Betriebsmetriken: Zaehlung nicht moeglich (%s)", sql);
            return 0;
        }
    }
}
