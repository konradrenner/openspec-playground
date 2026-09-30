package org.kore.raumschiffwerft.service.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.kore.raumschiffwerft.model.entity.AuftragsId;

/**
 * Fachliches Aggregat Kaufauftrag mit seinen Zustellungen. Verwaltet die
 * Zustellungen gemeinsam und beantwortet, ob der Auftrag vollstaendig
 * bestaetigt ist.
 */
public final class Kaufauftrag {

    private final AuftragsId auftragsId;
    private final org.kore.raumschiffwerft.model.entity.Kaufauftrag kaufauftrag;
    private final java.time.OffsetDateTime angenommenAm;
    private final List<Zustellung> zustellungen;

    public Kaufauftrag(AuftragsId auftragsId, org.kore.raumschiffwerft.model.entity.Kaufauftrag kaufauftrag,
                       java.time.OffsetDateTime angenommenAm, List<Zustellung> zustellungen) {
        this.auftragsId = Objects.requireNonNull(auftragsId);
        this.kaufauftrag = Objects.requireNonNull(kaufauftrag);
        this.angenommenAm = Objects.requireNonNull(angenommenAm);
        this.zustellungen = new ArrayList<>(Objects.requireNonNull(zustellungen));
    }

    public void zustellungHinzufuegen(Zustellung zustellung) {
        zustellungen.add(Objects.requireNonNull(zustellung));
    }

    /**
     * Der Auftrag ist erst dann fertig, wenn ALLE seiner Zustellungen
     * bestaetigt sind.
     */
    public boolean alleBestaetigt() {
        return !zustellungen.isEmpty()
                && zustellungen.stream().allMatch(z -> z.status() == Zustellungsstatus.BESTAETIGT);
    }

    public AuftragsId auftragsId() {
        return auftragsId;
    }

    public org.kore.raumschiffwerft.model.entity.Kaufauftrag kanonischerAuftrag() {
        return kaufauftrag;
    }

    public java.time.OffsetDateTime angenommenAm() {
        return angenommenAm;
    }

    public List<Zustellung> zustellungen() {
        return Collections.unmodifiableList(zustellungen);
    }
}
