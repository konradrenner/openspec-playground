# Design: annahme-und-zustellung

## Context

Der Service besteht aus BCE-Package-Gerüsten; Modell und Adapter sind aus dem Change kanonisches-modell-und-adapter fertig (`Zielsystem`-Port mit `zustellen(AuftragsId, Kaufauftrag)`, `statusAbfragen`, `ZustellungUngeklaert`). Postgres (`raumschiffwerft`/`raumschiffwerft`) läuft via devenv, WireMock liefert die Adapter-Teststubs. ArchUnit erzwingt Layering (service → adapter → model), BCE je Modul und Camel nur im Service. Motivation und Verhaltenskontrakte: proposal.md und specs/.

Klärung aus dem Planning-Dialog: `lieferplanet` ist im POST-Body die kanonische Nummer 1–60; das Flag targettet Nummern. Feste Zuordnung: **4 = „Yavin 4", 5 = „Hoth", 6 = „Dantooine"** (dokumentiert als Kommentare in flags.json).

## Goals / Non-Goals

**Goals:**

- Idempotente Auftragsannahme per REST mit klarer Antwort-/Fehlersemantik.
- Persistenz mit Flyway + plain JDBC, drei Tabellen, alles-oder-nichts-Annahme.
- Zustellungssteuerung: Flag-Wahl, synchrone Zustellung nach Commit aus dem Speicher, Verbuchung über die Zustandsmaschine des Aggregats.
- Testbarkeit: Unit-Tests (ohne `@QuarkusTest`) für Aggregat/Steuerung, ITs gegen echtes Postgres + WireMock.

**Non-Goals:**

- Kein Kafka-Konsum/-Versand (Outbox wird nur gefüllt; `gesendet_am` bleibt ungesetzt).
- Kein Retry-Scheduler und keine Lease-Übernahme in diesem Change — `naechster_versuch_um` und `lease_bis` werden gesetzt, aber von nichts gelesen. IN_ABGLEICH und FEHLGESCHLAGEN existieren in Enum/Schema, sind aber nicht erreichbar.
- Kein Ändern von Modell oder Adaptern; keine neuen ArchUnit-Regeln.

## Decisions

### D1: Struktur im Service nach BCE

- `entity`: REST-Beans (`KaufauftragAnfrage` mit Bean-Validation-Annotationen, `Auftragsstand`-Antwort-DTOs) und das fachliche Aggregat `Kaufauftrag` mit `Zustellung`-Entitäten (Zustandsmaschine als Enum `Zustellungsstatus` mit erlaubten Übergängen; Methoden `bestaetigt(externeReferenz)`/`ungeklaert(naechsterVersuchUm)` werfen bei illegalem Ausgangszustand).
- `control`: `AnnahmeController` (Transaktion, Idempotenz, Auftragsstand-Aufbau), `Zielsystemwahl` (OpenFeature), `Zustellungssteuerung` (Nach-Commit-Aufruf + Verbuchung), Repositories (`AuftragRepository`, `ZustellungRepository`, `OutboxRepository` — plain JDBC).
- `boundary`: REST-Resource `KaufauftraegeResource` (`@Path("/api/v1/kaufauftraege")`) — validiert Header/Body, delegiert an Control, übersetzt fachliche in HTTP-Ergebnisse — sowie die Camel-Route `direct:zustellen` (`ZustellRoute`, choice nach Zielsystemtyp) und den `CamelZustellport`. Hintergrund (Entscheidung aus dem Apply-Dialog): die projektwweite BCE-Regel verbietet control → boundary; die adapter- und camelberührenden Klassen liegen deshalb im boundary-Package des Service. Die Zustellungssteuerung in control hängt nur noch am Interface `Zustellport` (definiert in control, implementiert in boundary) und kennt weder Camel noch die Adapter.
- Zweite Korrektur aus dem Apply-Dialog: `Zielsystem` und `ZustellungUngeklaert` sind von `model.boundary` nach `model.entity` verschoben worden. Die checked Exception gehoert zum Port-Vertrag, den jede control-Klasse fuhren muss; unter der projektwweiten BCE-Regel waere jede Adapter-Nutzung aus service.control sonst unmoeglich. Die Haupt-Spec kanonisches-modell nennt keine Packages und bleibt unveraendert; das leere model.boundary-Package bleibt wegen der BCE-Paketstruktur bestehen.
- Bean Validation als `quarkus-hibernate-validator` auf den Entity-Beans; Header-UUID-Prüfung in der Resource (kein Standard-Constraint für Header). Fehler als 400.

### D2: Idempotenz-Transaktion (plain JDBC, `ON CONFLICT DO NOTHING`)

- `AnnahmeController.annehmen(...)` öffnet eine Transaktion (`ds.getConnection()` + `setAutoCommit(false)`; alternativ `UserTransaction` bei späterem Bedarf — KISS: direkte Connection-Verwaltung in einer Methode).
- `INSERT INTO auftrag ... ON CONFLICT (auftrags_id) DO NOTHING`: `executeUpdate() == 0` → Konflikt → bestehenden Stand per SELECT lesen, ohne Schreibzugriff, und zurückgeben.
- Danach im selben Commit-Verbund: INSERT journal_outbox, INSERT zustellung (status IN_ZUSTELLUNG, `lease_bis` = now + konfigurierbares Lease-Intervall, `instanz` = konfigurierbarer Pod-Name, Default `POD_NAME`-Env bzw. Hostname).
- Die Flag-Auswertung läuft VOR den Inserts innerhalb derselben Annahme (genau einmal pro Auftrag).
- DB nicht erreichbar → SQLTransientConnectionException o. ä. → 503 mit `Retry-After: 5` (fester Default, konfigurierbar `auftrag.retry-after-seconds`); nichts geschrieben, da Transaktion nie committet.

### D3: Zielsystemwahl per OpenFeature + flagd Datei-Modus

- Abhängigkeiten: OpenFeature Java SDK (`org.openfeature.javasdk` bzw. `dev.openfeature.java:openfeature-java`) und flagd-Provider im Datei-/Offline-Modus, der `flags/flags.json` direkt liest (kein flagd-Prozess, kein Docker — passt zur devenv-Philosophie). Exakte Artifact-Koordinaten werden beim Implementieren gegen die verfügbaren Versionen festgezurrt (Quarkiverse `quarkus-openfeature`-Erweiterung, sofern passend, sonst reine SDK-Beans).
- `flags/flags.json`: Flag `zielsystem`, Varianten `imperium`/`rebellion`, Default `imperium`, Targeting auf `lieferplanet` ∈ {4, 5, 6} → `rebellion` (Kommentar mit den Planetennamen). EvaluationContext: `targetingKey` = AuftragsId, Attribut `lieferplanet` = Nummer.
- Fallback: Providerfehler oder unbekannter Wert → `imperium` + Warn-Log (`Logger.warn`), Annahme läuft weiter. Mapping imperium → `Zielsystemtyp.IMPERIUM`, rebellion → `Zielsystemtyp.REBELLION`.

### D4: Zustellung nach Commit — Camel `direct:zustellen` mit choice

- RouteBuilder im Service: `from("direct:zustellen").choice().when(body/exchangeProperty zielsystemtyp == IMPERIUM).bean(imperiumZielsystem)...otherwise/when REBELLION → rebellionZielsystem`.
- Die Zustellungssteuerung ruft nach erfolgreichem Commit (im selben Request-Thread, synchron) `producerTemplate.send("direct:zustellen", ...)` mit AuftragsId + Kaufauftrag aus dem Speicher; kein DB-Select.
- Begründung: choice nach `Zielsystemtyp` hält die Auswahl deklarativ und Camel bleibt allein Sache des Service (ArchUnit-Kontrakt).

### D5: Verbuchung — genau ein konditionales Update pro Zustellung

- `UPDATE zustellung SET status=?, externe_referenz=?, versuche=versuche+1, aktualisiert_am=now WHERE auftrags_id=? AND zielsystem=? AND status='IN_ZUSTELLUNG'` — Erfolg: BESTAETIGT + externe Referenz; Misserfolg (`ZustellungUngeklaert`): UNGEKLAERT + `naechster_versuch_um` (now + konfigurierbares Intervall, Default 60 s).
- Das Aggregat entscheidet vor dem SQL über die Zustandsmaschine (illegaler Übergang → kein SQL); die Bedingung `status='IN_ZUSTELLUNG'` schützt gegen parallele Änderungen. Der Adapter bleibt reiner Port-Aufrufer ohne DB.

### D6: Flyway-Migration V1 (`V1__auftrag_zustellung_outbox.sql`)

- `auftrag`: `auftrags_id uuid PK, kaufauftrag jsonb NOT NULL, schema_version int NOT NULL, trace_id text, angenommen_am timestamptz NOT NULL`.
- `zustellung`: `PK (auftrags_id, zielsystem)`, `FK → auftrag(auftrags_id) ON DELETE RESTRICT`, `status text NOT NULL CHECK (in ...)`, `externe_referenz text`, `versuche int NOT NULL DEFAULT 0`, `naechster_versuch_um timestamptz`, `lease_bis timestamptz`, `instanz text`, `aktualisiert_am timestamptz NOT NULL`; `CREATE INDEX ... ON zustellung (auftrags_id) WHERE status IN ('IN_ZUSTELLUNG','UNGEKLAERT')` (partiell).
- `journal_outbox`: `id bigint GENERATED ALWAYS AS IDENTITY PK, auftrags_id uuid NOT NULL, payload jsonb NOT NULL, erstellt_am timestamptz NOT NULL, gesendet_am timestamptz`.
- Datasource: `quarkus.datasource.*` auf die devenv-Postgres; `quarkus.flyway.migrate-at-start=true`.

### D7: Trace-Kontext ohne neue Abhängigkeiten

- `trace_id`/`traceparent` aus dem aktuellen OpenTelemetry-Span via Handler (`io.opentelemetry.api.trace.Span.current()`, kommt mit Quarkus via `quarkus-opentelemetry`? — falls nicht, OTel-API ist transitiv über Quarkus vorhanden; andernfalls wird nur trace_id aus dem `traceparent`-Header des Requests gelesen und mitgespeichert, ohne aktives Tracing). KISS-Pfad: `traceparent`-Header des Requests lesen (W3C), trace_id daraus extrahieren; kein eigener Span-Zwang.

### D8: Teststrategie

- Unit-Tests (Surefire, kein `@QuarkusTest`): Zustandsmaschine des Aggregats, Zielsystemwahl (gemockter OpenFeature-Client inkl. Providerfehler-Fallback), Verbuchungs-Update-Bedingung, REST-Bean-Validation-Regeln.
- ITs (Failsafe, `@QuarkusTest`, Postgres + WireMock aus devenv): Annahme-Idempotenz (zweiter POST gleicher Key → kein Duplikat), 202/200-Pfade, 503 (DB gestoppt), GET/404, rebellion-Route über Planet 4, imperium-Fallback, UNGEKLAERT-Verbuchung über Jar-Jar-Stub.
- WireMock-Szenarien werden wie in den Adapter-ITs per Admin-API zurückgesetzt.

### D9: Umsetzungskorrekturen (Apply-Phase)

- `flags.json`: Der flagd-Parser der Quarkiverse-Extension erwartet `targeting` als einzelnes JsonLogic-Objekt (nicht als Array von Regeln wie flagd-standalone); Varianten als String sind für ein String-Flag korrekt.
- `Application.java` (@QuarkusMain) wurde entfernt: camel-quarkus startet den CamelContext nur automatisch, wenn KEINE @QuarkusMain-Klasse existiert — sonst erwartet es, dass der Main die CamelMainApplication startet (was in @QuarkusTest ohnehin nie läuft). Ohne eigene Main-Klasse startet Camel zuverlässig in Tests und Produktion.
- `camel-quarkus-direct` ergänzt: die Route braucht die direct-Komponente, die nicht in camel-quarkus-core enthalten ist.
- flagd-DevServices (Docker-Container) sind deaktiviert (`quarkus.openfeature.flagd.devservices.enabled=false`); die Auswertung läuft vollständig über den Datei-Modus. Die Adapter-Endpunkt-URLs stehen in den TEST-Properties des Service (die Adapter-Module bündeln ihre eigenen Test-Properties nicht im Artefakt).

## Risks / Trade-offs

- [Synchroner Zustellaufruf im Request-Thread verlängert die Antwortzeit] → bewusst (KISS, kein Scheduler in diesem Change); Adapter-Timeouts sind mit 2 s begrenzt, worst case 202 statt 200.
- [Flagd-Provider-Artefakte im Datei-Modus könnten je nach Version unterschiedlich konfiguriert sein] → Entscheidung D3 lässt die konkrete Anbindung bewusst offen und wird beim Implementieren festgezurrt; Fallback-Verhalten ist unabhängig davon spezifiziert.
- [Lease-Felder werden gesetzt, aber von nichts gelesen] → dokumentiertes Non-Goal; Felder sind Teil des Schema-Vertrags für spätere Changes.
- [Bei Commit-Erfolg, aber Absturz vor Verbuchung bleibt die Zeile IN_ZUSTELLUNG mit Lease] → genau der Fall, für den lease_bis existiert (spätere Übernahme); in diesem Change bleibt die Zeile offen, GET zeigt IN_ZUSTELLUNG.
- [UUID-Header-Prüfung ohne Bean-Validation-Standard] → eigene kleine Prüfung in der Resource; bleibt testbar (Unit-Test).

## Migration Plan

Rein additiv: neue Migration V1 läuft beim Start; kein Bestand. Rollback = Service zurückbauen, Schema-Objekte droppen (kein Produktivbetrieb). Kafka-Anbindung und Retry-Scheduler folgen als eigene Changes auf Basis des gefüllten Outbox und der Lease-Felder.

## Open Questions

Keine — die UUID-Zuordnung der Planeten (4/5/6) ist als Design-Entscheidung D3 dokumentiert und über den flagd-Kommentar änderbar, ohne den Spec-Vertrag zu berühren.
