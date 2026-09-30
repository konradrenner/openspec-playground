## MODIFIED Requirements

### Requirement: Tabelle journal_outbox
Die Tabelle `journal_outbox` MUSS die Spalten id, auftrags_id, payload (jsonb), erstellt_am, gesendet_am haben. Der Payload MUSS schemaVersion, auftragsId, empfangenAm, traceId, traceparent, rohPayload (der unverarbeitete Original-Body als String) und den kanonischen kaufauftrag enthalten. Die Tabelle wird von der Komponente kaufauftrag in der Annahme-Transaktion befuellt; `gesendet_am` bleibt ungesetzt, bis der Journal-Relay (Faehigkeit journal) die Zeile bestaetigt an Kafka versendet hat.

#### Scenario: Journaleintrag bei Annahme
- **WHEN** ein Auftrag angenommen wurde
- **THEN** existiert genau ein journal_outbox-Eintrag fuer die AuftragsId mit rohPayload (Original-Body als String), Trace-Kontext (traceId, traceparent) und kanonischem Auftrag

#### Scenario: Kein Versand in diesem Change
- **WHEN** ein Journaleintrag noch nicht vom Journal-Relay versendet wurde
- **THEN** ist sein gesendet_am ungesetzt; der Relay setzt es erst nach der bestaetigten Sendung (Details siehe Faehigkeit journal)
