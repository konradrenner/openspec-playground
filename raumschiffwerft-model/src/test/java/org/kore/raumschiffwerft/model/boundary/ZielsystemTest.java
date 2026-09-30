package org.kore.raumschiffwerft.model.boundary;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kore.raumschiffwerft.model.entity.AuftragsId;
import org.kore.raumschiffwerft.model.entity.Kaufauftrag;
import org.kore.raumschiffwerft.model.entity.Sternenzerstoererklasse;
import org.kore.raumschiffwerft.model.entity.Verarbeitungsstatus;
import org.kore.raumschiffwerft.model.entity.Zielsystemtyp;
import org.kore.raumschiffwerft.model.entity.Zustellbestaetigung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZielsystemTest {

    private final Zielsystem zielsystem = new Zielsystem() {
        @Override
        public Zielsystemtyp typ() {
            return Zielsystemtyp.IMPERIUM;
        }

        @Override
        public Zustellbestaetigung zustellen(AuftragsId auftragsId, Kaufauftrag auftrag) throws ZustellungUngeklaert {
            throw new ZustellungUngeklaert("Test-Misserfolg");
        }

        @Override
        public Verarbeitungsstatus statusAbfragen(AuftragsId auftragsId) {
            return Verarbeitungsstatus.von(Verarbeitungsstatus.Status.UNBEKANNT);
        }
    };

    @Test
    void portLaesstSichImplementieren() throws ZustellungUngeklaert {
        assertEquals(Zielsystemtyp.IMPERIUM, zielsystem.typ());
    }

    @Test
    void misserfolgWirdAlsCheckedExceptionGemeldet() {
        AuftragsId auftragsId = new AuftragsId(UUID.randomUUID());
        Kaufauftrag auftrag = new Kaufauftrag("Kaempfer", Sternenzerstoererklasse.EXECUTOR, 1, 1);
        assertThrows(ZustellungUngeklaert.class, () -> zielsystem.zustellen(auftragsId, auftrag));
    }

    @Test
    void statusAbfrageLiefertKanonischenStatus() throws ZustellungUngeklaert {
        Verarbeitungsstatus status = zielsystem.statusAbfragen(new AuftragsId(UUID.randomUUID()));

        assertEquals(Verarbeitungsstatus.Status.UNBEKANNT, status.status());
    }
}
