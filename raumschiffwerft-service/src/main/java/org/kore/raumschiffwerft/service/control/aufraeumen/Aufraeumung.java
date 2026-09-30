package org.kore.raumschiffwerft.service.control.aufraeumen;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Port der Aufraeum-Steuerung auf die Datenbank. Die Implementierung
 * (SQL, Advisory-Lock) liegt im boundary-Package der Aufraeum-Komponente;
 * die Steuerung kennt kein SQL.
 */
public interface Aufraeumung {

    /**
     * Versucht, die Aufraeum-Sperre zu setzen (pg_try_advisory_lock).
     * Rueckgabe false heisst: ein anderer Pod raeumt bereits auf, der
     * Durchlauf MUSS abbrechen. Nach true MUSS entsperren() laufen.
     */
    boolean sperren();

    /** Loescht gesendete journal_outbox-Zeilen in Batches von hoechstens batch Zeilen. */
    void gesendeteJournalZeilenLoeschen(int batch);

    /**
     * Liefert Loesch-Kandidaten: Auftraege, deren Annahme vor dem
     * Stichtag liegt und die keine nicht-bestaetigte Zustellung haben.
     */
    List<AuftragKandidat> kandidaten(OffsetDateTime stichtag, int batch);

    /** Loescht die Auftraege samt Zustellungszeilen (eine Transaktion). */
    void auftraegeLoeschen(List<UUID> auftragsIds);

    /** Gibt die Aufraeum-Sperre frei und schliesst die Connection. */
    void entsperren();
}
