# Proposal: annahme-und-zustellung

## Why

Der `raumschiffwerft-service` ist bisher ein Gerüst: Es existieren kanonisches Modell und beide Adapter, aber der Service hat weder eine REST-Schnittstelle noch Persistenz noch Zustellungssteuerung. Dieser Change etabliert die Kernanwendung: Aufträge annehmen (idempotent), wahlweise an Imperium oder Rebellion zustellen und den Zustand jederzeit abfragbar halten.

## What Changes

- **Neue REST-Schnittstelle**: `POST /api/v1/kaufauftraege` mit Pflicht-Header `Idempotency-Key` (UUID = AuftragsId) und Bean Validation; Antwort 200 nur wenn alle Zustellungen bestätigt sind, sonst 202 mit `Location` auf `GET /api/v1/kaufauftraege/{id}`; `GET` liefert den Stand, 404 für unbekannte IDs; Datenbank nicht erreichbar → 503 mit `Retry-After`, dann wurde nichts geschrieben.
- **Neue Persistenz** (Flyway + plain JDBC, kein ORM): drei Tabellen — `auftrag` (jsonb-Kaufauftrag, schema_version, trace_id, angenommen_am), `zustellung` (Statusmaschine IN_ZUSTELLUNG/IN_ABGLEICH/UNGEKLAERT/BESTAETIGT/FEHLGESCHLAGEN, externe_referenz, versuche, naechster_versuch_um, lease_bis, instanz, partieller Index auf offene Zustände), `journal_outbox` (Kafka-fähiger Journaleintrag mit rohem und kanonischem Payload). Zeilen werden nie gelöscht.
- **Idempotente Annahme in EINER Transaktion**: `INSERT auftrag ON CONFLICT DO NOTHING` als Idempotenz-Check (bei Konflikt bestehenden Stand zurückgeben, nichts weiter tun), Outbox-Eintrag und pro gewähltem Zielsystem eine `zustellung` als IN_ZUSTELLUNG mit Lease.
- **Neue Zustellungssteuerung**: Zielsystemwahl per OpenFeature-Flag `zielsystem` (flagd-Datei-Modus, `flags/flags.json`; nur imperium/rebellion; sonst und bei Providerfehler IMPERIUM mit Warn-Log; Planeten 4 „Yavin 4", 5 „Hoth", 6 „Dantooine" → rebellion), genau einmal pro Auftrag, vor dem Commit. Nach dem Commit synchron und ohne DB-Select per Camel-Route `direct:zustellen` (choice nach Zielsystemtyp) an den Adapter — das kanonische Modell kommt aus dem Speicher. Danach genau ein Update pro Zustellung per Primärschlüssel, nur bei erwartetem Ausgangsstatus: BESTAETIGT mit externer Referenz oder UNGEKLAERT mit naechster_versuch_um. Verbuchen macht die Zustellungssteuerung, nicht der Adapter.
- **Zustandsmaschine im Aggregat Kaufauftrag** mit seinen Zustellungen.
- Kafka wird in diesem Change NICHT benutzt (Outbox wird nur gefüllt).

Keine Breaking Changes zu bestehenden Specs; die Adapter- und Modellverträge bleiben unverändert genutzt.

## Capabilities

### New Capabilities

- `auftragsannahme`: REST-Vertrag der Annahme und Abfrage (Idempotency-Key, Bean Validation, 200/202/503/404, GET-Stand).
- `zustellungssteuerung`: Fachlicher Ablauf von der angenommenen Bestellung zur Zustellung — Zielsystemwahl per Feature-Flag, Zustellaufruf nach Commit, Verbuchung, Zustandsmaschine, Leases.
- `auftragsspeicherung`: Datenbankvertrag — Flyway-Migrationen, drei Tabellen, idempotente Annahme-Transaktion, Journaleintrag, keine Löschungen.

### Modified Capabilities

Keine — bestehende Specs (`build-structure`, `module-architecture`, `local-dev-environment`, `kanonisches-modell`, `adapter-imperium`, `adapter-rebellion`) bleiben unverändert; der Change hält Layering (service → adapter → model), BCE und Camel-Freiheit der Adapter ein.

## Impact

- **Code**: `raumschiffwerft-service` (bisher nur Package-Gerüst): boundary (REST-Resource), control (Annahme, Zustellungssteuerung, Zustellverbuchung, Flag-Auswertung, Camel-Route), entity (Aggregat `Kaufauftrag` mit Zustellungen, REST-Beans).
- **Abhängigkeiten (neu im Service-POM)**: `quarkus-flyway`, `quarkus-jdbc-postgresql`/Agroal, `quarkus-hibernate-validator`, OpenFeature-Java-SDK mit flagd-Provider (Datei-Modus); Camel (`camel-quarkus-core`, bereits deklariert) für die `direct:zustellen`-Route.
- **Datenbank**: neue Flyway-Migrationen im Service; Schema `raumschiffwerft` (Postgres aus devenv).
- **Konfiguration**: `flags/flags.json` (flagd-Datei-Modus), Datasource-Properties; Kein Kafka, keine Änderung an devenv-Diensten.
- **Nicht betroffen**: Modell- und Adapter-Module (nur Nutzung der bestehenden Ports), WireMock-Stubs (werden für die Integrationstests weiter genutzt).
