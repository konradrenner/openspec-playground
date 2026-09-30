# Spec-Delta: auftragsspeicherung

## Purpose

Beschreibt den Datenbankvertrag des Services: Flyway-Migrationen mit plain JDBC (kein ORM), die drei Tabellen auftrag, zustellung und journal_outbox sowie die idempotente Annahme in einer einzigen Transaktion.

## ADDED Requirements

### Requirement: Migrationen per Flyway, Zugriff per plain JDBC
Das Schema MUSS per Flyway-Migrationen versioniert werden. Der Datenbankzugriff MUSS als plain JDBC (DataSource, SQL) erfolgen; ein ORM (JPA/Hibernate) ist NICHT erlaubt.

#### Scenario: Schema entsteht per Migration
- **WHEN** der Service gegen eine leere Postgres-Datenbank startet
- **THEN** legt Flyway die Tabellen auftrag, zustellung und journal_outbox in versionierter Form an

### Requirement: Tabelle auftrag
Die Tabelle `auftrag` MUSS folgende Spalten haben: auftrags_id (UUID, Primaerschluessel), kaufauftrag (jsonb, kanonischer Auftrag), schema_version, trace_id, angenommen_am.

#### Scenario: Angenommener Auftrag liegt vollstaendig
- **WHEN** ein Auftrag angenommen wurde
- **THEN** existiert genau eine auftrag-Zeile mit der AuftragsId, dem kanonischen Auftrag als jsonb, der Schema-Version, der Trace-Id und dem Annahmezeitpunkt

### Requirement: Tabelle zustellung
Die Tabelle `zustellung` MUSS den Primaerschluessel (auftrags_id, zielsystem) und einen Fremdschluessel auf auftrag haben sowie die Spalten status (IN_ZUSTELLUNG, IN_ABGLEICH, UNGEKLAERT, BESTAETIGT, FEHLGESCHLAGEN), externe_referenz, versuche, naechster_versuch_um, lease_bis, instanz, aktualisiert_am. Es MUSS ein partieller Index auf offene Zustaende existieren.

#### Scenario: Zustellungszeilen je Zielsystem
- **WHEN** ein Auftrag mit einem gewaehlten Zielsystem angenommen wurde
- **THEN** existiert genau eine zustellung-Zeile mit dem Primaerschluessel (auftrags_id, zielsystem)

#### Scenario: Partizipationspruefung offener Zustaende
- **WHEN** offene Zustellungen gesucht werden (status IN_ZUSTELLUNG oder UNGEKLAERT)
- **THEN** nutzt die Datenbank den partiellen Index, ohne abgeschlossene Zustaende zu scannen

### Requirement: Tabelle journal_outbox
Die Tabelle `journal_outbox` MUSS die Spalten id, auftrags_id, payload (jsonb), erstellt_am, gesendet_am haben. Der Payload MUSS schemaVersion, auftragsId, empfangenAm, traceId, traceparent, rohPayload (der unverarbeitete Original-Body als String) und den kanonischen kaufauftrag enthalten. In diesem Change wird die Tabelle nur befuellt; gesendet_am bleibt ungesetzt.

#### Scenario: Journaleintrag bei Annahme
- **WHEN** ein Auftrag angenommen wurde
- **THEN** existiert genau ein journal_outbox-Eintrag fuer die AuftragsId mit rohPayload (Original-Body als String), Trace-Kontext (traceId, traceparent) und kanonischem Auftrag

#### Scenario: Kein Versand in diesem Change
- **WHEN** ein Journaleintrag existiert
- **THEN** ist gesendet_am nicht gesetzt, weil kein Versand (Kafka) implementiert ist

### Requirement: Annahme in EINER Transaktion
Die Annahme MUSS in EINER Datenbanktransaktion erfolgen: INSERT in auftrag mit ON CONFLICT DO NOTHING als Idempotenz-Check, der Outbox-Eintrag und pro gewaehltem Zielsystem eine zustellung als IN_ZUSTELLUNG mit Lease. Bei Konflikt (Auftrag existiert bereits) MUSS der bestehende Stand zurueckgegeben werden, OHNE dass Outbox-Eintrag oder Zustellungszeilen erneut geschrieben werden. Schlägt die Transaktion fehl, MUSS nichts geschrieben bleiben.

#### Scenario: Atomare Annahme
- **WHEN** ein neuer Auftrag angenommen wird
- **THEN** werden auftrag-Zeile, journal_outbox-Eintrag und die zustellung-Zeilen zusammen sichtbar (alles oder nichts)

#### Scenario: Konflikt beim Idempotenz-Check
- **WHEN** ein Auftrag mit bereits existierender auftrags_id erneut angenommen wird
- **THEN** wird nichts Neues geschrieben und der bestehende Stand (auftrag inkl. zustellung) zurueckgegeben

#### Scenario: Fehlerhafter Abbruch laesst nichts zurueck
- **WHEN** die Annahme-Transaktion vor dem Commit fehlschlaegt
- **THEN** existiert keine auftrag-Zeile, kein Journaleintrag und keine Zustellungszeile dieses Auftrags
