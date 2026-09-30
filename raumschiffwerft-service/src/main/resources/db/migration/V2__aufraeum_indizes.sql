-- Indizes fuer das Aufraeumen (Change aufraeumen)
-- Rein additiv: keine Spalten- oder Tabellenaenderung.

-- Batchloeschung gesendeter Journaleintraege
CREATE INDEX idx_journal_outbox_gesendet
    ON journal_outbox (id)
    WHERE gesendet_am IS NOT NULL;

-- NOT EXISTS-Prefilter der Kandidatensuche: Auftraege mit
-- nicht-bestaetigten Zustellungen ohne Vollscan ausschliessen
CREATE INDEX idx_zustellung_nicht_bestaetigt
    ON zustellung (auftrags_id)
    WHERE status <> 'BESTAETIGT';

-- Altersfilter der Aufbewahrungsregel
CREATE INDEX idx_auftrag_angenommen_am
    ON auftrag (angenommen_am);
