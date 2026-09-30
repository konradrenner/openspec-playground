-- Annahme und Zustellung von Kaufauftraegen (Change annahme-und-zustellung)
-- Plain JDBC als Zugriffsweg; kein ORM.

CREATE TABLE auftrag (
    auftrags_id    uuid PRIMARY KEY,
    kaufauftrag    jsonb NOT NULL,
    schema_version int   NOT NULL,
    trace_id       text,
    angenommen_am  timestamptz NOT NULL
);

CREATE TABLE zustellung (
    auftrags_id          uuid NOT NULL REFERENCES auftrag (auftrags_id),
    zielsystem           text NOT NULL,
    status               text NOT NULL
        CHECK (status IN ('IN_ZUSTELLUNG', 'IN_ABGLEICH', 'UNGEKLAERT', 'BESTAETIGT', 'FEHLGESCHLAGEN')),
    externe_referenz     text,
    versuche             int  NOT NULL DEFAULT 0,
    naechster_versuch_um timestamptz,
    lease_bis            timestamptz,
    instanz              text,
    aktualisiert_am      timestamptz NOT NULL,
    PRIMARY KEY (auftrags_id, zielsystem)
);

-- Offene Zustellungen gezielt finden, ohne abgeschlossene zu scannen
CREATE INDEX idx_zustellung_offen
    ON zustellung (auftrags_id)
    WHERE status IN ('IN_ZUSTELLUNG', 'UNGEKLAERT');

CREATE TABLE journal_outbox (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auftrags_id uuid NOT NULL,
    payload     jsonb NOT NULL,
    erstellt_am timestamptz NOT NULL,
    gesendet_am timestamptz
);
