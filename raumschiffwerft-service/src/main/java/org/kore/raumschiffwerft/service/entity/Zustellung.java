package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

/**
 * Eine Zustellung eines Auftrags an genau ein Zielsystem, einschliesslich
 * ihrer Zustandsmaschine: IN_ZUSTELLUNG -> BESTAETIGT / UNGEKLAERT (Erstzustellung),
 * IN_ZUSTELLUNG / UNGEKLAERT / IN_ABGLEICH -> IN_ABGLEICH (Beanspruchen durch
 * die Abgleich-Route) und IN_ABGLEICH -> BESTAETIGT / UNGEKLAERT / FEHLGESCHLAGEN.
 * Illegale Uebergaenge werden abgelehnt und fuehren zu keinem Schreibzugriff.
 */
public final class Zustellung {

    private final AuftragsId auftragsId;
    private final Zielsystemtyp zielsystem;
    private Zustellungsstatus status;
    private String externeReferenz;
    private int versuche;
    private OffsetDateTime naechsterVersuchUm;
    private OffsetDateTime leaseBis;
    private String instanz;
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
     * Verbucht eine erfolgreiche Zustellung: erlaubt vom Ausgangszustand
     * IN_ZUSTELLUNG (Erstzustellung, zaehlt den Versuch) oder IN_ABGLEICH
     * (Abgleich, der Versuch wurde beim Beanspruchen gezaehlt).
     */
    public void bestaetigen(String externeReferenz) {
        ausgangPruefen(Zustellungsstatus.IN_ZUSTELLUNG, Zustellungsstatus.IN_ABGLEICH);
        versuchZaehlenBeiErstzustellung();
        status = Zustellungsstatus.BESTAETIGT;
        this.externeReferenz = Objects.requireNonNull(externeReferenz);
        this.naechsterVersuchUm = null;
        aktualisiertAm = OffsetDateTime.now();
    }

    /**
     * Verbucht eine ungeklaerte Zustellung mit dem Zeitpunkt des naechsten
     * Versuchs: erlaubt vom Ausgangszustand IN_ZUSTELLUNG oder IN_ABGLEICH.
     */
    public void ungeklaertErklaeren(OffsetDateTime naechsterVersuchUm) {
        ausgangPruefen(Zustellungsstatus.IN_ZUSTELLUNG, Zustellungsstatus.IN_ABGLEICH);
        versuchZaehlenBeiErstzustellung();
        status = Zustellungsstatus.UNGEKLAERT;
        this.naechsterVersuchUm = Objects.requireNonNull(naechsterVersuchUm);
        aktualisiertAm = OffsetDateTime.now();
    }

    /**
     * Verbucht das endgueltige Scheitern nach max-versuchen: erlaubt nur
     * vom Ausgangszustand IN_ABGLEICH.
     */
    public void fehlgeschlagenErklaeren() {
        ausgangPruefen(Zustellungsstatus.IN_ABGLEICH);
        status = Zustellungsstatus.FEHLGESCHLAGEN;
        this.naechsterVersuchUm = null;
        aktualisiertAm = OffsetDateTime.now();
    }

    /**
     * Beansprucht die Zustellung fuer den Abgleich: erlaubt von
     * IN_ZUSTELLUNG (abgelaufene Lease), UNGEKLAERT (faelliger Versuch)
     * und IN_ABGLEICH (erneut abgelaufene Lease). Der Uebergang erhoehen
     * versuche - wie das atomare Beanspruchs-Statement in der Datenbank,
     * wenn das Aggregat aus dem Vorzustand aufgebaut wird.
     */
    public void beanspruchen(OffsetDateTime leaseBis, String instanz) {
        ausgangPruefen(Zustellungsstatus.IN_ZUSTELLUNG, Zustellungsstatus.UNGEKLAERT,
                Zustellungsstatus.IN_ABGLEICH);
        versuche++;
        status = Zustellungsstatus.IN_ABGLEICH;
        this.leaseBis = Objects.requireNonNull(leaseBis);
        this.instanz = Objects.requireNonNull(instanz);
        aktualisiertAm = OffsetDateTime.now();
    }

    private void versuchZaehlenBeiErstzustellung() {
        if (status == Zustellungsstatus.IN_ZUSTELLUNG) {
            versuche++;
        }
    }

    private void ausgangPruefen(Zustellungsstatus... erlaubteAusgaenge) {
        for (Zustellungsstatus erlaubt : erlaubteAusgaenge) {
            if (status == erlaubt) {
                return;
            }
        }
        throw new IllegalStateException(
                "Ungueltiger Uebergang von %s (erlaubt nur von %s)"
                        .formatted(status, java.util.Arrays.toString(erlaubteAusgaenge)));
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
