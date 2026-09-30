package org.kore.raumschiffwerft.service.control.aufraeumen;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.kore.raumschiffwerft.service.entity.Zustellungsstatus;

/**
 * Ein Loesch-Kandidat des Aufraeumens: Auftrags-ID, Annahmezeitpunkt und
 * die Zustellungen des Auftrags - genug fuer die Aufbewahrungsregel,
 * um die Loeschbarkeit abschliessend zu entscheiden.
 */
public record AuftragKandidat(UUID auftragsId, OffsetDateTime angenommenAm,
                              List<Zustellungsstatus> zustaende) {

    public AuftragKandidat {
        Objects.requireNonNull(auftragsId);
        Objects.requireNonNull(angenommenAm);
        zustaende = List.copyOf(zustaende);
    }
}
