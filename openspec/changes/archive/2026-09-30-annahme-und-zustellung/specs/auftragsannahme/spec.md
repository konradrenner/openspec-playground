# Spec-Delta: auftragsannahme

## Purpose

Definiert den REST-Vertrag für das Annehmen und Abfragen von Kaufaufträgen: idempotente Annahme mit Pflicht-Header, Bean Validation, klare Antwort- und Fehlersemantik (200/202/503/404).

## ADDED Requirements

### Requirement: Annahme per POST mit Pflicht-Header Idempotency-Key
Aufträge MÜSSEN über `POST /api/v1/kaufauftraege` angenommen werden. Der Header `Idempotency-Key` MUSS Pflicht sein und MUSS eine UUID enthalten; diese UUID IST die AuftragsId. Ein fehlender oder nicht-UUID-fähiger Header MUSS mit 400 abgelehnt werden, ohne dass etwas gespeichert oder zugestellt wird.

#### Scenario: Gueltige Annahme
- **WHEN** `POST /api/v1/kaufauftraege` mit gueltigem Body und Header `Idempotency-Key` mit UUID aufgerufen wird
- **THEN** wird der Auftrag unter dieser UUID angenommen und verarbeitet

#### Scenario: Fehlender Idempotency-Key
- **WHEN** `POST /api/v1/kaufauftraege` ohne Header `Idempotency-Key` aufgerufen wird
- **THEN** antwortet der Service mit 400 und speichert nichts

#### Scenario: Idempotency-Key ist keine UUID
- **WHEN** der Header `Idempotency-Key` einen Wert enthaelt, der keine UUID ist
- **THEN** antwortet der Service mit 400 und speichert nichts

### Requirement: Bean Validation des Auftrags-Bodys
Der Body MUSS Bean-Validation-Regeln erfuellen: kaeufer (1 bis 100 Zeichen), klasse (IMPERIAL_I, IMPERIAL_II, VICTORY, EXECUTOR), anzahl (1 bis 12), lieferplanet (Nummer 1 bis 60). Ein Verstoß MUSS mit 400 abgelehnt werden, ohne dass etwas gespeichert oder zugestellt wird.

#### Scenario: Ungueltiger Body
- **WHEN** der Body eine dieser Regeln verletzt (z. B. anzahl 13, lieferplanet 0 oder leerer kaeufer)
- **THEN** antwortet der Service mit 400 und speichert nichts

### Requirement: Antwort nur bei vollstaendig bestaetigten Zustellungen 200
Die Annahme MUSS mit 200 antworten, nur wenn ALLE Zustellungen des Auftrags bestaetigt (BESTAETIGT) sind. Andernfalls MUSS sie mit 202 antworten und einen `Location`-Header auf `GET /api/v1/kaufauftraege/{id}` liefern.

#### Scenario: Alle Zustellungen bestaetigt
- **WHEN** nach der Annahme und Zustellung jede Zustellung des Auftrags BESTAETIGT ist
- **THEN** antwortet der Service mit 200

#### Scenario: Noch nicht alle bestaetigt
- **WHEN** nach der Annahme mindestens eine Zustellung nicht BESTAETIGT ist (z. B. UNGEKLAERT)
- **THEN** antwortet der Service mit 202 und `Location: /api/v1/kaufauftraege/{id}`

### Requirement: Idempotentes Verhalten bei wiederholter Annahme
Ein wiederholter `POST` mit demselben `Idempotency-Key` MUSS den bestehenden Auftragsstand zurueckgeben, OHNE eine zweite Zustellung auszuloesen oder zusaetzliche Zeilen zu schreiben (siehe Spec-Delta auftragsspeicherung, Annahme-Transaktion).

#### Scenario: Erneuter POST mit gleichem Key
- **WHEN** derselbe Auftrag (gleicher `Idempotency-Key`) erneut gepostet wird, nachdem er bereits angenommen wurde
- **THEN** liefert der Service den bestehenden Stand (200/202 nach denselben Regeln wie die Erstannahme) und es entsteht kein Duplikat und keine erneute Zustellung

### Requirement: Datenbank nicht erreichbar liefert 503
Ist die Datenbank bei der Annahme nicht erreichbar, MUSS der Service mit 503 und `Retry-After`-Header antworten. Es MUSS nichts geschrieben worden sein.

#### Scenario: Datenbank ausgefallen
- **WHEN** die Datenbank bei der Annahme nicht erreichbar ist
- **THEN** antwortet der Service mit 503, einem `Retry-After`-Header, und es wurde nichts persistiert

### Requirement: Abfrage per GET liefert den Auftragsstand
`GET /api/v1/kaufauftraege/{id}` MUSS den Stand aus `auftrag` und seinen `zustellung`-Zeilen liefern. Fuer unbekannte IDs MUSS 404 geliefert werden.

#### Scenario: Bekannter Auftrag
- **WHEN** `GET /api/v1/kaufauftraege/{id}` mit einer vorhandenen AuftragsId aufgerufen wird
- **THEN** liefert der Service die Auftragsdaten und den Stand aller Zustellungen

#### Scenario: Unbekannter Auftrag
- **WHEN** `GET /api/v1/kaufauftraege/{id}` mit einer nicht vorhandenen AuftragsId aufgerufen wird
- **THEN** antwortet der Service mit 404
