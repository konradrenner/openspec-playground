package org.kore.raumschiffwerft.service.control;

import java.util.Optional;

import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.service.entity.Kaufauftrag;

/**
 * Port des control auf die Auftragstabelle. Die Implementierung (JDBC)
 * liegt in boundary/persistence; das control kennt kein SQL.
 */
public interface AuftragRepository {

    /**
     * Legt den Auftrag an (ON CONFLICT DO NOTHING als Idempotenz-Check).
     * Rueckgabe 0 heisst: Auftrag existierte bereits. Laeuft innerhalb
     * der aktuellen Transaktion, falls eine besteht.
     */
    int anlegen(AuftragsId auftragsId, String kaufauftragJson, int schemaVersion,
                String traceId, java.time.OffsetDateTime angenommenAm);

    /**
     * Liest den vollstaendigen Stand (auftrag samt zustellung-Zeilen);
     * leer, wenn die AuftragsId unbekannt ist.
     */
    Optional<Kaufauftrag> stand(AuftragsId auftragsId);
}
