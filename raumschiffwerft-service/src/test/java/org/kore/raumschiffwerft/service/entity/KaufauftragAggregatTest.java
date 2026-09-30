package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KaufauftragAggregatTest {

    private final OffsetDateTime jetzt = OffsetDateTime.now();
    private final AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
    private final org.kore.raumschiffwerft.model.entity.Kaufauftrag kaufauftrag =
            new org.kore.raumschiffwerft.model.entity.Kaufauftrag("Tarkin", Sternenzerstoererklasse.IMPERIAL_I, 2, 7);

    private Zustellung zustellung(Zustellungsstatus status) {
        return new Zustellung(auftragsId, Zielsystemtyp.IMPERIUM, status, null, 0, null,
                jetzt.plusMinutes(10), "test", jetzt);
    }

    @Test
    void alleBestaetigtNurWennJedeZustellungBestaetigtIst() {
        Kaufauftrag offen = new Kaufauftrag(auftragsId, kaufauftrag, jetzt, List.of(zustellung(Zustellungsstatus.IN_ZUSTELLUNG)));
        assertFalse(offen.alleBestaetigt());

        Kaufauftrag ungeklaert = new Kaufauftrag(auftragsId, kaufauftrag, jetzt,
                List.of(zustellung(Zustellungsstatus.UNGEKLAERT)));
        assertFalse(ungeklaert.alleBestaetigt());

        Zustellung bestaetigt = zustellung(Zustellungsstatus.IN_ZUSTELLUNG);
        bestaetigt.bestaetigen("ISD-4711");
        Kaufauftrag fertig = new Kaufauftrag(auftragsId, kaufauftrag, jetzt, List.of(bestaetigt));
        assertTrue(fertig.alleBestaetigt());
    }

    @Test
    void ohneZustellungenIstDerAuftragNichtBestaetigt() {
        assertFalse(new Kaufauftrag(auftragsId, kaufauftrag, jetzt, List.of()).alleBestaetigt());
    }

    @Test
    void zustellungenWerdenVerwaltet() {
        Kaufauftrag auftrag = new Kaufauftrag(auftragsId, kaufauftrag, jetzt, List.of(zustellung(Zustellungsstatus.IN_ZUSTELLUNG)));
        auftrag.zustellungHinzufuegen(zustellung(Zustellungsstatus.IN_ZUSTELLUNG));

        assertEquals(2, auftrag.zustellungen().size());
        assertEquals(kaufauftrag, auftrag.kanonischerAuftrag());
        assertThrows(UnsupportedOperationException.class, () -> auftrag.zustellungen().add(
                zustellung(Zustellungsstatus.IN_ZUSTELLUNG)));
    }
}
