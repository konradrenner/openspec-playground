## Why

Die Annahme schreibt Journaleintraege in `journal_outbox`, aber nichts versendet sie: `gesendet_am` bleibt ungesetzt, der Datenbestand ist fuer niemanden auswertbar. Der Change schliesst die Kette: ein Outbox-Relay versendet die Eintraege zuverlaessig nach Kafka, eine Consumer-Route indexiert sie in OpenSearch — jeweils mit den in devenv bereits laufenden Diensten.

## What Changes

- **Outbox-Relay als Timer-Route**: beansprucht ungesendete `journal_outbox`-Zeilen in Batches (`SELECT ... FOR UPDATE SKIP LOCKED`), sendet den Payload an das Kafka-Topic `durchlauferhitzer.journal` (Key = Auftrags-ID, `acks=all`, idempotenter Producer, synchron auf das Ack warten) und setzt danach `gesendet_am`. Doppelte Sendungen nach einem Absturz sind erlaubt (at-least-once).
- **Kafka-Ausfall ist unkritisch**: Ist Kafka nicht erreichbar, aendert sich an der Annahme nichts — die Outbox staut sich, der Relay wiederholt sich, der Rueckstand wird nachgeholt. Keine Journaleintrag geht verloren.
- **Consumer-Route indexiert das Journal**: konsumiert `durchlauferhitzer.journal` und indexiert jeden Eintrag in den OpenSearch-Index `durchlauferhitzer-journal` mit der Auftrags-ID als Dokument-ID; der Kafka-Commit erfolgt erst nach erfolgreichem Indexieren. Doppelte Eintraege sind damit idempotent (Dokument wird ueberschrieben).
- **Explizites Index-Mapping beim Start**: der Index wird beim Start mit explizitem Mapping angelegt (falls noch nicht vorhanden); `rohPayload` wird nicht indiziert.
- **Komponentengrenze kaufauftrag/journal**: die Komponente kaufauftrag schreibt die Outbox-Zeile in ihrer Annahme-Transaktion (unveraendert); die neue Komponente journal besitzt Relay und Indexierung. Kopplung nur ueber das Tabellenformat, keine Compile-Abhaengigkeit zwischen den Komponenten.
- **Tests**: IT, in der ein angenommener Auftrag mit Trace-ID als Journaleintrag in OpenSearch erscheint (Awaitility).

Keine Aenderungen am REST-Vertrag, am Zustell-/Abgleich-Ablauf, am Schema (alle Spalten existieren) oder an Modell/Adaptern.

## Capabilities

### New Capabilities

- `journal`: Fachlicher Vertrag der Journal-Kette — Outbox-Relay nach Kafka (Batches, SKIP LOCKED, at-least-once, gesendet_am), Verhalten bei Kafka-Ausfall, Consumer-Indexierung nach OpenSearch (Dokument-ID, Commit nach Indexierung, Mapping ohne rohPayload) und die Komponentengrenze zu kaufauftrag.

### Modified Capabilities

- `auftragsspeicherung`: Die Anforderung „Tabelle journal_outbox" aendert sich — `gesendet_am` wird nicht mehr dauerhaft ungesetzt gelassen, sondern vom Journal-Relay nach bestaetigtem Versand gesetzt; das Szenario „Kein Versand in diesem Change" entfaellt entsprechend.

## Impact

- **Code (nur `raumschiffwerft-service`)**: neue Komponente journal (`control`/`boundary`, eigene Sub-Packages): Relay-Steuerung und Outbox-Lese-Repository, Kafka- und OpenSearch-Anbindung in boundary (Camel-Route timer→kafka, Consumer-Route kafka→Indexierung, OpenSearch-Client), ArchUnit-Regel fuer die Komponentengrenze. kaufauftrag bleibt unberuehrt (Outbox-Schreibzugriff ist bereits Teil der Annahme-Transaktion).
- **Abhaengigkeiten**: `camel-quarkus-kafka` (Producer/Consumer-Routen); OpenSearch-Indexierung ueber den im Stack vorhandenen REST-Client (quarkus-rest-client) — keine schwere Client-Bibliothek noetig.
- **Konfiguration**: Kafka-Bootstrap existiert (`kafka.bootstrap.servers=localhost:9092`); neu: Relay-Timer-Periode, Batchgroesse, Topic-/Index-Namen (konfigurierbar, Defaults `durchlauferhitzer.journal` / `durchlauferhitzer-journal`), OpenSearch-URL, Consumer-Gruppe; Test-Profil mit kurzer Timer-Periode.
- **Bestehende Tests**: `AnnahmeIT` behauptet heute `gesendet_am IS NULL` — muss angepasst werden, da der Relay asynchron versendet; die Abgleich-ITs sind davon unberuehrt (sie raeumen die Tabellen ohnehin).
- **Nicht betroffen**: `raumschiffwerft-model`, Adapter, Flyway-Schema (keine Migration — `gesendet_am` existiert), REST-Vertrag, Zustell-/Abgleich-Steuerung.
- **Betrieb**: Kafka (9092) und OpenSearch (9200) laufen bereits via devenv; der Relay liefert den aufgelaufenen Bestand (z. B. Zeilen aus frueheren Integrationstests) beim ersten Lauf ab — Testdaten werden in der IT-Umgebung dafuer bereinigt.
