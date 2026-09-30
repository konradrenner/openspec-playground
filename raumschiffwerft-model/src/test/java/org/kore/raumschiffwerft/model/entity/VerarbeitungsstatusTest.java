package org.kore.raumschiffwerft.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerarbeitungsstatusTest {

    @Test
    void statusOhneExterneReferenz() {
        Verarbeitungsstatus status = Verarbeitungsstatus.von(Verarbeitungsstatus.Status.IN_BEARBEITUNG);

        assertEquals(Verarbeitungsstatus.Status.IN_BEARBEITUNG, status.status());
        assertNull(status.externeReferenz());
    }

    @Test
    void statusMitExternerReferenz() {
        Verarbeitungsstatus status = Verarbeitungsstatus
                .mitExternerReferenz(Verarbeitungsstatus.Status.ABGESCHLOSSEN, "RB-1138");

        assertEquals(Verarbeitungsstatus.Status.ABGESCHLOSSEN, status.status());
        assertEquals("RB-1138", status.externeReferenz());
    }

    @Test
    void alleStatuswerteSindNutzbar() {
        assertEquals(3, Verarbeitungsstatus.Status.values().length);
        assertDoesNotThrow(() -> Verarbeitungsstatus.von(Verarbeitungsstatus.Status.ABGESCHLOSSEN));
        assertDoesNotThrow(() -> Verarbeitungsstatus.von(Verarbeitungsstatus.Status.IN_BEARBEITUNG));
        assertDoesNotThrow(() -> Verarbeitungsstatus.von(Verarbeitungsstatus.Status.UNBEKANNT));
    }

    @Test
    void ungueltigeWerteWerdenAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> Verarbeitungsstatus.von(null));
        assertThrows(IllegalArgumentException.class,
                () -> Verarbeitungsstatus.mitExternerReferenz(Verarbeitungsstatus.Status.UNBEKANNT, null));
        assertThrows(IllegalArgumentException.class,
                () -> Verarbeitungsstatus.mitExternerReferenz(Verarbeitungsstatus.Status.UNBEKANNT, " "));
    }
}
