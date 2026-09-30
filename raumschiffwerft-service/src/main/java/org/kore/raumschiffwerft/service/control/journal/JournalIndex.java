package org.kore.raumschiffwerft.service.control.journal;

/**
 * Port der Journal-Indexierung auf den OpenSearch-Index. Die
 * Implementierung liegt im boundary-Package der Komponente journal
 * (REST-Client); der Indexer kennt weder OpenSearch noch HTTP.
 */
public interface JournalIndex {

    /**
     * Speichert den Journaleintrag als Dokument unter der Auftrags-ID
     * (idempotent: dieselbe Dokument-ID ueberschreibt). Jeder Misserfolg
     * wird als RuntimeException gemeldet.
     */
    void speichern(String auftragsId, String payload);
}
