## 1. Abhaengigkeiten, Konfiguration und Komponentengeruest

- [x] 1.1 Service-POM erweitern: `camel-quarkus-kafka`, `quarkus-rest-client` (+ Jackson) — verifiziert durch `mvn -pl raumschiffwerft-service -am compile` in der devenv-Shell
- [x] 1.2 Konfiguration in `application.properties`: `journal.topic` (durchlauferhitzer.journal), `journal.index` (durchlauferhitzer-journal), `journal.opensearch.url` (http://localhost:9200), `journal.consumer-group`, `journal.relay.timer-period-millis` (2000), `journal.relay.batch` (50), idempotenter Producer (`camel.component.kafka.configuration.enable-idempotence=true`); Test-Profil mit Timer-Periode 500 ms — verifiziert durch Start des Service (Index wird angelegt, Routen registriert)
- [x] 1.3 Sub-Packages `control.journal` / `boundary.journal` anlegen und Architekturtest `ComponentArchitectureTest` ergaenzen: keine Abhaengigkeiten zwischen journal und kaufauftrag in beide Richtungen — verifiziert durch `mvn -pl raumschiffwerft-service test` (ArchUnit gruen)

## 2. Outbox-Relay nach Kafka

- [x] 2.1 `JournalOutboxRepository` (control.journal): ungesendete Zeilen in Batches beanspruchen (`FOR UPDATE SKIP LOCKED`) und gesendet_am setzen (plain JDBC, kurze Transaktion) — verifiziert durch die Unit-Tests zu 2.2 und die ITs in Gruppe 4
- [x] 2.2 Port `JournalVersand` (control.journal) + `JournalRelay` (Batch-Transaktion: beanspruchen → je Zeile synchron senden → gesendet_am setzen → Commit; Sendefehler ohne gesendet_am, Rest im naechsten Durchlauf) — verifiziert durch Unit-Tests mit gemocktem Repository/Port (Erfolg, Sendefehler, leere Outbox)
- [x] 2.3 `JournalRelayRoute` (boundary.journal): `direct:journalVersenden` → `to("kafka:...?brokers=...&acks=all")` mit `kafka.KEY` = Auftrags-ID; Timer-Route startet `JournalRelay.relay()` — verifiziert durch die JournalRelayIT in Gruppe 4

## 3. Indexierung nach OpenSearch

- [x] 3.1 `OpenSearchIndexClient` (boundary.journal, Quarkus-REST-Client): Index pruefen (`GET /{index}`), Index mit explizitem Mapping anlegen (`PUT /{index}`, `rohPayload` mit `"enabled": false`), Dokument speichern (`PUT /{index}/_doc/{auftragsId}`); Mapping-Anlage beim Start via `StartupEvent` — verifiziert durch die JournalRelayIT (Index existiert mit Mapping) und einen Unit-Test des Mapping-Aufbaus
- [x] 3.2 Port `JournalIndex` (control.journal) + `JournalIndexer` (Indexieren mit Auftrags-ID als Dokument-ID; Fehler wird gemeldet, kein Commit) — verifiziert durch Unit-Tests (Erfolg, Indexierfehler)
- [x] 3.3 `JournalConsumerRoute` (boundary.journal): `from("kafka:...")` mit `autoCommitDisable=true` + manuellen Acks → `JournalIndexer.indexiere(...)` → Ack erst nach erfolgreichem Indexieren — verifiziert durch die JournalRelayIT

## 4. Integrationstests

- [x] 4.1 `AnnahmeIT.wiederholteAnnahmeSchreibtKeinDuplikat` anpassen: statt `gesendet_am IS NULL` nur noch Existenz und Eindeutigkeit des Journaleintrags pruefen (Versand laeuft asynchron) — verifiziert durch `mvn -pl raumschiffwerft-service verify`
- [x] 4.2 `JournalRelayIT`: Annahme per POST mit `Idempotency-Key` und gueltigem `traceparent`-Header → mit Awaitility auf das OpenSearch-Dokument warten (`GET /durchlauferhitzer-journal/_doc/{auftragsId}`, `traceId` im `_source`) und `gesendet_am` in der DB pruefen — verifiziert durch `mvn -pl raumschiffwerft-service verify` (Failsafe)

## 5. Gesamtsicherung

- [x] 5.1 `mvn verify` im Wurzelverzeichnis (devenv-Shell): Unit-, Architektur- und Integrationstests aller vier Module — verifiziert durch gruenen Build
- [x] 5.2 Specs syncen und Change archivieren (`openspec`-Workflow), Haupt-Specs `journal` (neu) und `auftragsspeicherung` (modifiziert) aktualisieren — verifiziert durch `openspec validate` und `openspec status`
