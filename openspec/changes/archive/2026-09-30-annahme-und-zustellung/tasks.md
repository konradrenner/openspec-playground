# Tasks: annahme-und-zustellung

## 1. Abhängigkeiten, Konfiguration und Schema

- [x] 1.1 Service-POM erweitern (quarkus-flyway, quarkus-jdbc-postgresql, quarkus-hibernate-validator, OpenFeature-Java-SDK mit flagd-Provider Datei-Modus) und Datasource-Konfiguration in application.properties setzen (devenv-Postgres raumschiffwerft) — verifiziert durch `mvn -pl raumschiffwerft-service -am compile` und gestarteten QuarkusTest, der Flyway ausführt
- [x] 1.2 Flyway-Migration `V1__auftrag_zustellung_outbox.sql` anlegen: auftrag (PK auftrags_id, kaufauftrag jsonb, schema_version, trace_id, angenommen_am), zustellung (PK (auftrags_id, zielsystem), FK auftrag, status CHECK mit fünf Werten, externe_referenz, versuche, naechster_versuch_um, lease_bis, instanz, aktualisiert_am, partieller Index auf offene Zustaende), journal_outbox (id, auftrags_id, payload jsonb, erstellt_am, gesendet_am) — verifiziert per IT, der gegen die migrierte Datenbank alle drei Tabellen nutzt
- [x] 1.3 `flags/flags.json` anlegen: Flag `zielsystem`, Varianten imperium/rebellion, Default imperium, Targeting lieferplanet in {4, 5, 6} → rebellion, mit Kommentaren (4=„Yavin 4", 5=„Hoth", 6=„Dantooine") — verifiziert durch Unit-Test der Zielsystemwahl und Auswertung der Datei im IT

## 2. Aggregat und Zustandsmaschine (entity)

- [x] 2.1 REST-Beans anlegen: `KaufauftragAnfrage` (Bean Validation: kaeufer 1–100, klasse aus IMPERIAL_I/IMPERIAL_II/VICTORY/EXECUTOR, anzahl 1–12, lieferplanet 1–60) und Antwort-DTOs (Auftragsstand mit Zustellungen) — verifiziert durch Unit-Tests der Validation (gültige und je eine ungültige Ausprägung je Feld)
- [x] 2.2 Enum `Zustellungsstatus` (IN_ZUSTELLUNG, IN_ABGLEICH, UNGEKLAERT, BESTAETIGT, FEHLGESCHLAGEN) und Aggregat `Kaufauftrag` mit `Zustellung`-Entitäten anlegen: Übergänge IN_ZUSTELLUNG→BESTAETIGT (mit externer Referenz) und IN_ZUSTELLUNG→UNGEKLAERT (mit naechster_versuch_um), illegale Übergänge werden abgelehnt; IN_ABGLEICH/FEHLGESCHLAGEN vorhanden, aber ohne Übergänge — verifiziert durch Unit-Tests für alle legalen und illegalen Übergänge

## 3. Persistenz (control, plain JDBC)

- [x] 3.1 Repositories anlegen (AuftragRepository, ZustellungRepository, OutboxRepository) mit plain JDBC und den SQL-Statements aus dem Schema-Vertrag — verifiziert durch Unit-Tests mit gemockter DataSource/Connection für SQL-Parameterbindung und durch IT 4.3
- [x] 3.2 `AnnahmeController.annehmen(...)` implementieren: EINE Transaktion mit INSERT auftrag ON CONFLICT DO NOTHING (0 Zeilen → bestehenden Stand laden, nichts weiter schreiben), INSERT journal_outbox (payload: schemaVersion, auftragsId, empfangenAm, traceId, traceparent aus Request-Header, rohPayload als String, kanonischer kaufauftrag), INSERT zustellung pro Zielsystem (IN_ZUSTELLUNG, lease_bis, instanz) — verifiziert durch Unit-Tests (Konflikt- und Normalfall) und IT 4.3/4.4
- [x] 3.3 DB nicht erreichbar → fachlicher Fehler, der in der Resource als 503 mit Retry-After (konfigurierbar, Default 5 s) endet, ohne dass etwas committet wurde — verifiziert durch Unit-Test der Fehlerübersetzung und IT gegen gestopptes Postgres

## 4. Annahme-REST (boundary) und Zustellungssteuerung (control)

- [x] 4.1 `KaufauftraegeResource` anlegen: POST /api/v1/kaufauftraege (Pflicht-Header Idempotency-Key als UUID, sonst 400; Bean Validation, sonst 400; Antwort 200 nur wenn alle Zustellungen BESTAETIGT, sonst 202 mit Location) und GET /api/v1/kaufauftraege/{id} (Stand aus auftrag und zustellung, 404 unbekannt) — verifiziert durch Unit-Tests (Header/Body-Fehler, Antwortwahl) und IT 4.3
- [x] 4.2 `Zielsystemwahl` implementieren: OpenFeature-Flag `zielsystem` genau einmal pro Auftrag vor dem Commit, nur imperium/rebellion, sonst und bei Providerfehler imperium mit Warn-Log, Mapping auf Zielsystemtyp — verifiziert durch Unit-Tests (Planet 4/5/6 → rebellion; anderer Planet → imperium; Providerfehler → imperium + Warn-Log; genau-einmal-Aufruf)
- [x] 4.3 `Zustellungssteuerung` + Camel-Route implementieren: Route `direct:zustellen` mit choice nach Zielsystemtyp auf die Adapter-Beans; nach Commit synchroner ProducerTemplate-Aufruf mit AuftragsId und Kaufauftrag aus dem Speicher (kein DB-Select); danach je Zustellung GENAU EIN konditionales Update per PK nur bei status IN_ZUSTELLUNG (BESTAETIGT + externe Referenz bzw. UNGEKLAERT + naechster_versuch_um, versuche+1, aktualisiert_am) — verifiziert durch Unit-Tests mit gemocktem ProducerTemplate/Repository (kein Update bei unerwartetem Ausgangsstatus) und IT gegen WireMock
- [x] 4.4 Gesamtabfrage: GET liefert auftrag + alle zustellungen; Antwort der Annahme (200/202) richtet sich nach dem aggregierten Stand — verifiziert durch IT

## 5. Integrationstests und Gesamtbau

- [x] 5.1 `AnnahmeIT` (@QuarkusTest, Postgres + WireMock): erfolgreiche Annahme via imperium (Standardfall, Bestellnummer ISD-4711 → 200), Annahme mit lieferplanet 4 → rebellion (RB-1138 → 200), Jar-Jar-Käufer → Zustellung UNGEKLAERT → 202 + Location, anschließend GET zeigt UNGEKLAERT mit naechster_versuch_um — verifiziert durch `mvn -pl raumschiffwerft-service verify` mit laufenden devenv-Diensten
- [x] 5.2 Idempotenz- und Fehler-IT: zweiter POST mit gleichem Idempotency-Key liefert bestehenden Stand und schreibt keine zweite auftrag-/zustellung-/outbox-Zeile (Zeilen zählen), fehlender/ungültiger Header → 400 ohne Schreiben, ungültiger Body → 400 ohne Schreiben, GET unbekannt → 404, gestopptes Postgres → 503 mit Retry-After — verifiziert durch den gleichen IT-Lauf
- [x] 5.3 Gesamtbau und Architektur: `mvn verify` im Wurzelverzeichnis — verifiziert durch grünen Build inkl. aller Adapter-ITs, ArchUnit-Regeln (Layering, BCE, Camel nur im Service, keine @QuarkusTest-Unit-Tests) und ohne Datenmüll (keine gelöschten Zeilen; Outbox ohne gesendet_am)
