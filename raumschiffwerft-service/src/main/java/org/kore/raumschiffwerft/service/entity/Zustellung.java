package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

/**
 * Eine Zustellung eines Auftrags an genau ein Zielsystem, einschliesslich
 * ihrer Zustandsmaschine: In diesem Change ist nur der Uebergang
 * IN_ZUSTELLUNG -> BESTAETIGT / UNGEKLAERT erlaubt; illegale Uebergaenge
 * werden abgelehnt und fuehren zu keinem Schreibzugriff.
 */
public final class Zustellung {

    private final AuftragsId auftragsId;
    private final Zielsystemtyp zielsystem;
    private Zustellungsstatus status;
    private String externeReferenz;
    private int versuche;
    private OffsetDateTime naechsterVersuchUm;
    private final OffsetDateTime leaseBis;
    private final String instanz;
    private OffsetDateTime aktualisiertAm;

    public Zustellung(AuftragsId auftragsId, Zielsystemtyp zielsystem, Zustellungsstatus status,
                     String externeReferenz, int versuche, OffsetDateTime naechsterVersuchUm,
                     OffsetDateTime leaseBis, String instanz, OffsetDateTime aktualisiertAm) {
        this.auftragsId = Objects.requireNonNull(auftragsId);
        this.zielsystem = Objects.requireNonNull(zielsystem);
        this.status = Objects.requireNonNull(status);
        this.externeReferenz = externeReferenz;
        this.versuche = versuche;
        this.naechsterVersuchUm = naechsterVersuchUm;
        this.leaseBis = leaseBis;
        this.instanz = instanz;
        this.aktualisiertAm = Objects.requireNonNull(aktualisiertAm);
    }

    /**
     * Verbucht eine erfolgreiche Zustellung: genau erlaubt vom
     * Ausgangszustand IN_ZUSTELLUNG aus.
     */
    public void bestaetigen(String externeReferenz) {
        wechsleZu(Zustellungsstatus.BESTAETIGT);
        this.externeReferenz = Objects.requireNonNull(externeReferenz);
    }

    /**
     * Verbucht eine ungeklaerte Zustellung mit dem Zeitpunkt des
     * naechsten Versuchs; genau erlaubt vom Ausgangszustand
     * IN_ZUSTELLUNG aus.
     */
    public void ungeklaertErklaeren(OffsetDateTime naechsterVersuchUm) {
        wechsleZu(Zustellungsstatus.UNGEKLAERT);
        this.naechsterVersuchUm = Objects.requireNonNull(naechsterVersuchUm);
    }

    private void wechsleZu(Zustellungsstatus ziel) {
        if (status != Zustellungsstatus.IN_ZUSTELLUNG) {
            throw new IllegalStateException(
                    "Ungueltiger Uebergang von %s nach %s (erlaubt nur von IN_ZUSTELLUNG)"
                            .formatted(status, ziel));
        }
        status = ziel;
        versuche++;
        aktualisiertAm = OffsetDateTime.now();
    }

    public AuftragsId auftragsId() {
        return auftragsId;
    }

    public Zielsystemtyp zielsystem() {
        return zielsystem;
    }

    public Zustellungsstatus status() {
        return status;
    }

    public String externeReferenz() {
        return externeReferenz;
    }

    public int versuche() {
        return versuche;
    }

    public OffsetDateTime naechsterVersuchUm() {
        return naechsterVersuchUm;
    }

    public OffsetDateTime leaseBis() {
        return leaseBis;
    }

    public String instanz() {
        return instanz;
    }

    public OffsetDateTime aktualisiertAm() {
        return aktualisiertAm;
    }
}
