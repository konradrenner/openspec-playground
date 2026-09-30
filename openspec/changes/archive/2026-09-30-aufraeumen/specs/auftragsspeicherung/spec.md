## ADDED Requirements

### Requirement: Partielle Indizes fuer das Aufraeumen
Fuer die Loeschabfragen des Aufraeumens MUeSSEN partielle Indizes existieren (Flyway-Migration V2): ein partieller Index auf `journal_outbox` (id) fuer Zeilen mit gesetztem gesendet_am und ein partieller Index auf `zustellung` (auftrags_id) fuer Zeilen mit Status ungleich BESTAETIGT. Zusaetzlich MUSS ein Index auf `auftrag` (angenommen_am) die Altersfilterung stuetzen.

#### Scenario: Indizes existieren nach der Migration
- **WHEN** der Service gegen eine Datenbank mit Schema-Version 1 startet
- **THEN** legt Flyway V2 die Indizes fuer die Aufraeum-Abfragen an, ohne bestehende Tabellen oder Spalten zu veraendern
