package org.kore.raumschiffwerft.service.control;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;
import org.kore.raumschiffwerft.model.entity.ZustellungUngeklaert;
import org.kore.raumschiffwerft.service.entity.Backoff;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Abgleichssteuerung: beansprucht periodisch faellige Zustellungen
 * (UNGEKLAERT mit faelligem Versuchszeitpunkt oder abgelaufene Lease in
 * IN_ZUSTELLUNG/IN_ABGLEICH) atomar, fragt IMMER zuerst den Status beim
 * gespeicherten Zielsystem ab und verbucht das Ergebnis. Nur UNBEKANNT
 * fuehrt zu einem Neuversand ueber den Zustellport. Nach max-versuchen
 * wird die Zustellung endgueltig als FEHLGESCHLAGEN verbucht. Ein Fehler
 * bei einer Zeile bricht den Durchlauf nicht ab. Zaehlungen laufen
 * ausschliesslich ueber die OpenTelemetry API.
 */
@ApplicationScoped
public class Abgleichssteuerung {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Abgleichssteuerung.class.getName());

    private final ZustellungRepository zustellungRepository;
    private final Abgleichsport abgleichsport;
    private final Zustellport zustellport;
    private final Backoff backoff;
    private final Tracer tracer;
    private final LongCounter zustellungsErgebnisse;
    private final LongCounter leaseUebernahmen;
    private final long leaseSekunden;
    private final int maxVersuche;
    private final int batch;
    private final String instanz;

    @Inject
    public Abgleichssteuerung(ZustellungRepository zustellungRepository,
                              Abgleichsport abgleichsport,
                              Zustellport zustellport,
                              OpenTelemetry openTelemetry,
                              @ConfigProperty(name = "zustellung.wiederholung-sekunden") long basisSekunden,
                              @ConfigProperty(name = "abgleich.backoff-max-sekunden") long backoffMaxSekunden,
                              @ConfigProperty(name = "abgleich.jitter-anteil") double jitterAnteil,
                              @ConfigProperty(name = "abgleich.max-versuche") int maxVersuche,
                              @ConfigProperty(name = "abgleich.batch") int batch,
                              @ConfigProperty(name = "zustellung.lease-sekunden") long leaseSekunden,
                              @ConfigProperty(name = "zustellung.instanz") String instanz) {
        this.zustellungRepository = zustellungRepository;
        this.abgleichsport = abgleichsport;
        this.zustellport = zustellport;
        this.backoff = new Backoff(basisSekunden, backoffMaxSekunden, jitterAnteil, new Random());
        this.tracer = openTelemetry.getTracer("durchlauferhitzer.abgleich");
        Meter meter = openTelemetry.getMeterProvider().get("durchlauferhifter");
        this.zustellungsErgebnisse = meter.counterBuilder("durchlauferhifter.zustellungen").build();
        this.leaseUebernahmen = meter.counterBuilder("durchlauferhifter.lease.abgelaufen").build();
        this.maxVersuche = maxVersuche;
        this.batch = batch;
        this.leaseSekunden = leaseSekunden;
        this.instanz = instanz;
    }

    /**
     * Ein Durchlauf des Abgleichs. Fehler beim Beanspruchen beenden den
     * Durchlauf, Fehler einer einzelnen Zeile nicht.
     */
    public void abgleichen() {
        List<Beanspruchung> faellige;
        try {
            faellige = zustellungRepository.faelligeBeanspruchen(batch,
                    OffsetDateTime.now().plusSeconds(leaseSekunden), instanz);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Abgleich: Beanspruchen faelliger Zustellungen gescheitert", e);
            return;
        }
        for (Beanspruchung beanspruchung : faellige) {
            try {
                verarbeiten(beanspruchung);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Abgleich: Verarbeitung einer Zustellung gescheitert (auftragsId="
                        + beanspruchung.zustellung().auftragsId().wert() + ", zielsystem="
                        + beanspruchung.zustellung().zielsystem() + ")", e);
            }
        }
    }

    /**
     * Verarbeitet eine beanspruchte Zustellung im eigenen Abgleich-Span
     * (Attribute auftrag.id, zielsystem, ausgangsstatus); eine nach
     * abgelaufener Lease uebernommene Zustellung zaehlt den Lease-Counter.
     */
    private void verarbeiten(Beanspruchung beanspruchung) {
        Zustellung zustellung = beanspruchung.zustellung();
        zaehlenLeaseUebernahme(beanspruchung);
        Span span = tracer.spanBuilder("abgleich")
                .setAttribute("durchlauferhitzer.auftrag.id", zustellung.auftragsId().wert().toString())
                .setAttribute("durchlauferhitzer.zielsystem", zustellung.zielsystem().name())
                .setAttribute("durchlauferhitzer.zustellstatus",
                        beanspruchung.ausgangsstatus().name())
                .startSpan();
        try (Scope ignoriert = span.makeCurrent()) {
            statusAbgleichen(beanspruchung, zustellung);
        } finally {
            span.end();
        }
    }

    /** Absturz-Uebernahme: Ausgangszustand stammte aus einer abgelaufenen Lease. */
    private void zaehlenLeaseUebernahme(Beanspruchung beanspruchung) {
        if (beanspruchung.ausgangsstatus() != Zustellungsstatus.UNGEKLAERT) {
            leaseUebernahmen.add(1, Attributes.of(
                    AttributeKey.stringKey("ausgangsstatus"), beanspruchung.ausgangsstatus().name()));
        }
    }

    private void statusAbgleichen(Beanspruchung beanspruchung, Zustellung zustellung) {
        Verarbeitungsstatus status;
        try {
            status = abgleichsport.statusAbfragen(zustellung.auftragsId(), zustellung.zielsystem());
        } catch (ZustellungUngeklaert e) {
            LOG.log(Level.WARNING, "Abgleich: Statusabfrage gescheitert (auftragsId="
                    + zustellung.auftragsId().wert() + ", zielsystem=" + zustellung.zielsystem() + ")",
                    e);
            ungeklaertVerbuchen(zustellung, e);
            return;
        }
        switch (status.status()) {
            case ABGESCHLOSSEN -> abgeschlossenVerbuchen(zustellung, status);
            case IN_BEARBEITUNG -> ungeklaertVerbuchen(zustellung, null);
            case UNBEKANNT -> neuversandVerbuchen(beanspruchung, zustellung);
        }
    }

    private void abgeschlossenVerbuchen(Zustellung zustellung, Verarbeitungsstatus status) {
        if (status.externeReferenz() == null) {
            LOG.log(Level.WARNING, "Abgleich: ABGESCHLOSSEN ohne externe Referenz, Zustellung bleibt "
                    + "offen (auftragsId=" + zustellung.auftragsId().wert() + ", zielsystem="
                    + zustellung.zielsystem() + ")");
            ungeklaertVerbuchen(zustellung, null);
            return;
        }
        zustellung.bestaetigen(status.externeReferenz());
        verbuchen(zustellung);
    }

    private void neuversandVerbuchen(Beanspruchung beanspruchung, Zustellung zustellung) {
        try {
            Zustellbestaetigung bestaetigung = zustellport.zustellen(zustellung.auftragsId(),
                    beanspruchung.kaufauftrag(), zustellung.zielsystem());
            zustellung.bestaetigen(bestaetigung.externeReferenz());
        } catch (ZustellungUngeklaert e) {
            LOG.log(Level.WARNING, "Abgleich: Neuversand gescheitert (auftragsId="
                    + zustellung.auftragsId().wert() + ", zielsystem=" + zustellung.zielsystem() + ")",
                    e);
            ungeklaertVerbuchen(zustellung, e);
            return;
        }
        verbuchen(zustellung);
    }

    private void ungeklaertVerbuchen(Zustellung zustellung, Throwable ursache) {
        if (zustellung.versuche() >= maxVersuche) {
            LOG.log(Level.SEVERE, "Zustellung nach " + zustellung.versuche()
                    + " Versuchen endgueltig gescheitert (auftragsId=" + zustellung.auftragsId().wert()
                    + ", zielsystem=" + zustellung.zielsystem() + ")", ursache);
            zustellung.fehlgeschlagenErklaeren();
        } else {
            zustellung.ungeklaertErklaeren(
                    backoff.naechsterVersuchUm(OffsetDateTime.now(), zustellung.versuche()));
        }
        verbuchen(zustellung);
    }

    private void verbuchen(Zustellung zustellung) {
        zustellungRepository.verbuchen(zustellung, Zustellungsstatus.IN_ABGLEICH);
        zustellungsErgebnisse.add(1, Attributes.of(
                AttributeKey.stringKey("zielsystem"), zustellung.zielsystem().name(),
                AttributeKey.stringKey("ergebnis"), zustellung.status().name()));
    }
}
