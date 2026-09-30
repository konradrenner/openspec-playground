package org.kore.raumschiffwerft.service.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZustellungsZustandsmaschineTest {

    private final OffsetDateTime jetzt = OffsetDateTime.now();

    private Zustellung zustellung(Zustellungsstatus status) {
        return new Zustellung(new AuftragsId(UUID.randomUUID()), Zielsystemtyp.IMPERIUM, status,
                null, 0, null, jetzt.plusMinutes(10), "test", jetzt);
    }

    @Test
    void bestaetigenVonInZustellungIstErlaubt() {
        Zustellung zustellung = zustellung(Zustellungsstatus.IN_ZUSTELLUNG);

        zustellung.bestaetigen("ISD-4711");

        assertEquals(Zustellungsstatus.BESTAETIGT, zustellung.status());
        assertEquals("ISD-4711", zustellung.externeReferenz());
        assertEquals(1, zustellung.versuche());
    }

    @Test
    void ungeklaertVonInZustellungIstErlaubt() {
        Zustellung zustellung = zustellung(Zustellungsstatus.IN_ZUSTELLUNG);
        OffsetDateTime naechsterVersuch = jetzt.plusSeconds(60);

        zustellung.ungeklaertErklaeren(naechsterVersuch);

        assertEquals(Zustellungsstatus.UNGEKLAERT, zustellung.status());
        assertEquals(naechsterVersuch, zustellung.naechsterVersuchUm());
        assertNull(zustellung.externeReferenz());
        assertEquals(1, zustellung.versuche());
    }

    @Test
    void illegaleUebergaengeWerdenAbgelehnt() {
        for (Zustellungsstatus ausgang : Zustellungsstatus.values()) {
            if (ausgang == Zustellungsstatus.IN_ZUSTELLUNG) {
                continue;
            }
            Zustellung bestaetigt = zustellung(ausgang);
            assertThrows(IllegalStateException.class, () -> bestaetigt.bestaetigen("ISD-4711"),
                    "bestaetigen von %s".formatted(ausgang));

            Zustellung ungeklaert = zustellung(ausgang);
            assertThrows(IllegalStateException.class, () -> ungeklaert.ungeklaertErklaeren(jetzt.plusSeconds(60)),
                    "ungeklaertErklaeren von %s".formatted(ausgang));
        }
    }

    @Test
    void nachVerbuchungIstKeinZweiterUebergangMoeglich() {
        Zustellung zustellung = zustellung(Zustellungsstatus.IN_ZUSTELLUNG);
        zustellung.bestaetigen("ISD-4711");

        assertThrows(IllegalStateException.class, () -> zustellung.ungeklaertErklaeren(jetzt));
        assertThrows(IllegalStateException.class, () -> zustellung.bestaetigen("RB-1138"));
    }
}
