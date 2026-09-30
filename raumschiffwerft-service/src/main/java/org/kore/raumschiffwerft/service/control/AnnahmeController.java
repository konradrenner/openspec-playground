package org.kore.raumschiffwerft.service.control;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.service.entity.Kaufauftrag;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;
import org.kore.raumschiffwerft.service.entity.Zustellung;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agroal.api.AgroalDataSource;

/**
 * Annahme eines Kaufauftrags in EINER Transaktion: Zielsystemwahl vor dem
 * Commit, INSERT auftrag mit ON CONFLICT DO NOTHING als Idempotenz-Check,
 * Journaleintrag und Zustellungszeile(n) mit Lease. Bei Konflikt wird der
 * bestehende Stand zurueckgegeben und nichts weiter geschrieben.
 */
@ApplicationScoped
public class AnnahmeController {

    private static final int SCHEMA_VERSION = 1;

    private final AgroalDataSource dataSource;
    private final AuftragRepository auftragRepository;
    private final OutboxRepository outboxRepository;
    private final ZustellungRepository zustellungRepository;
    private final Zielsystemwahl zielsystemwahl;
    private final ObjectMapper objectMapper;
    private final long leaseSekunden;
    private final String instanz;

    @Inject
    public AnnahmeController(AgroalDataSource dataSource, AuftragRepository auftragRepository,
                             OutboxRepository outboxRepository, ZustellungRepository zustellungRepository,
                             Zielsystemwahl zielsystemwahl, ObjectMapper objectMapper,
                             @ConfigProperty(name = "zustellung.lease-sekunden") long leaseSekunden,
                             @ConfigProperty(name = "zustellung.instanz") String instanz) {
        this.dataSource = dataSource;
        this.auftragRepository = auftragRepository;
        this.outboxRepository = outboxRepository;
        this.zustellungRepository = zustellungRepository;
        this.zielsystemwahl = zielsystemwahl;
        this.objectMapper = objectMapper;
        this.leaseSekunden = leaseSekunden;
        this.instanz = instanz;
    }

    /**
     * Nimmt den Auftrag an. Rueckgabe enthaelt den Auftragsstand und ob es
     * sich um eine Neuannahme (true) oder einen Idempotenz-Treffer (false)
     * handelt.
     */
    public AnnahmeErgebnis annehmen(AuftragsId auftragsId,
                                   org.kore.raumschiffwerft.model.entity.Kaufauftrag kaufauftrag,
                                   String rohPayload, String traceparent) {
        // Genau eine Flag-Auswertung pro Auftrag, vor dem Commit
        var zielsystemtyp = zielsystemwahl.waehlen(auftragsId, kaufauftrag);
        OffsetDateTime jetzt = OffsetDateTime.now();
        String traceId = TraceKontext.traceId(traceparent);

        Connection verbindung;
        try {
            verbindung = dataSource.getConnection();
        } catch (SQLException | RuntimeException e) {
            throw new DatenbankNichtErreichbar(e);
        }

        try (verbindung) {
            verbindung.setAutoCommit(false);
            int eingefuegt = auftragRepository.anlegen(verbindung, auftragsId,
                    kanonischesJson(kaufauftrag), SCHEMA_VERSION, traceId, jetzt);
            if (eingefuegt == 0) {
                verbindung.rollback();
                return new AnnahmeErgebnis(
                        auftragRepository.stand(auftragsId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Konflikt ohne bestehenden Auftrag: " + auftragsId.wert())),
                        false);
            }

            outboxRepository.journalEinfuegen(verbindung, auftragsId,
                    journalPayload(auftragsId, kaufauftrag, rohPayload, traceparent, traceId, jetzt), jetzt);

            Zustellung zustellung = new Zustellung(auftragsId, zielsystemtyp,
                    Zustellungsstatus.IN_ZUSTELLUNG, null, 0, null,
                    jetzt.plusSeconds(leaseSekunden), instanz, jetzt);
            zustellungRepository.anlegen(verbindung, zustellung);

            verbindung.commit();
            return new AnnahmeErgebnis(
                    new Kaufauftrag(auftragsId, kaufauftrag, jetzt, List.of(zustellung)), true);
        } catch (SQLException | RuntimeException e) {
            try {
                verbindung.rollback();
            } catch (SQLException rollbackFehler) {
                e.addSuppressed(rollbackFehler);
            }
            throw new IllegalStateException("Annahme gescheitert (auftragsId=%s)"
                    .formatted(auftragsId.wert()), e);
        }
    }

    private String kanonischesJson(org.kore.raumschiffwerft.model.entity.Kaufauftrag kaufauftrag) {
        try {
            return objectMapper.writeValueAsString(kaufauftrag);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Kanonischer Auftrag nicht serialisierbar", e);
        }
    }

    private String journalPayload(AuftragsId auftragsId,
                                  org.kore.raumschiffwerft.model.entity.Kaufauftrag kaufauftrag,
                                  String rohPayload, String traceparent, String traceId,
                                  OffsetDateTime empfangenAm) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("auftragsId", auftragsId.wert().toString());
        payload.put("empfangenAm", empfangenAm.toString());
        if (traceId == null) {
            payload.putNull("traceId");
        } else {
            payload.put("traceId", traceId);
        }
        if (traceparent == null) {
            payload.putNull("traceparent");
        } else {
            payload.put("traceparent", traceparent.trim());
        }
        payload.put("rohPayload", rohPayload);
        payload.set("kaufauftrag", objectMapper.valueToTree(kaufauftrag));
        return payload.toString();
    }
}
