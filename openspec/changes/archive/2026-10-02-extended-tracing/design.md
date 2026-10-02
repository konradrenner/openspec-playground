## Context

Der Service nutzt `quarkus-opentelemetry` (HTTP-Request-Spans, Log-/Metrik-Export) und setzt drei manuelle Spans (`journal.relay`, `journal.indexieren`, `abgleich`, siehe `JournalRelay`, `JournalConsumerRoute`, `Abgleichssteuerung`). Es fehlen Spans fuer Camel-Routenschritte (`camel-quarkus-opentelemetry` ist nicht im Classpath), fuer JDBC-Zugriffe (plain JDBC ueber Agroal, `Verbindungen` verteilt die Connections; Quarkus instrumentiert plain JDBC nicht) und fuer Adapter-Aufrufe (SOAP-Client des Imperiums ist nicht automatisch instrumentiert). Siehe proposal.md - Why.

## Goals / Non-Goals

**Goals:**

- Ein Auftrag ist im Trace Ende-zu-Ende nachvollziehbar: Annahme, jeder Routenschritt, jeder DB-Zugriff, jeder externe Aufruf, Journal-Verarbeitung.
- Externe Aufrufe heben sich im Trace klar ab (eigener Client-Span mit Zielsystem-Attribut).
- Bestehende Span-Attribute (`durchlauferhitzer.auftrag.id`, `.zielsystem`, `.zustellstatus`) und Verknuepfungen (Relay-Span-Link, Kafka-Header-Propagation) bleiben erhalten.

**Non-Goals:**

- Kein fachlicher Verhaltensaenderung an REST, Persistenz, Zustell- oder Abgleichlogik.
- Keine Aenderung an OTLP-Endpunkten, Signalen oder Collector-Konfiguration.
- Kein Tail-Based Sampling; Span-Volumen steigt bewusst in Kauf (Dev-Umgebung, Default-Sampling).
- Keine Instrumentierung der externen Systeme selbst (WireMock ist nicht unser Code).

## Decisions

### 1. Camel-Spans via `camel-quarkus-opentelemetry`

Die Extension wird im `raumschiffwerft-service` ergaenzt. Der Camel-OpenTelemetry-Tracer instrumentiert Routen und Processor-Schritte automatisch und haengt an den aktiven Kontext (Annahme-Request-Span bzw. die manuellen Abgleich-/Journal-Spans); er bindet sich an die CDI-`OpenTelemetry`-Instanz von Quarkus.

- Alternative: manuelle Spans in jedem Processor — verworfen, weil invasiv, fehleranfaellig und mit dem Route-Bau (choice, bean-validator) unzureichend abgedeckt.
- Span-Namen kommen von `routeId` (bereits pfleglich gesetzt: `zustellen`, `abgleich`, `statusAbfragen`, `journal-relay`, `journalVersenden`, Journal-Consumer) bzw. Processor-Position; keine zusaetzlichen Umbenennungen noetig.
- Ausschliesslich die Timer-Routen laufen ohnehin ohne fachlichen Eltern-Span (root) — akzeptiert, so wie heute auch der `abgleich`-Timer.

### 2. DB-Spans ueber einen Connection-Wrapper in `Verbindungen`

`Verbindungen.oeffnen()` liefert kuenftig eine duenne Wrapper-Connection (dynamischer Proxy), die jede Statement-Ausfuehrung (`executeQuery`, `executeUpdate`, `execute`) als Span `db.zugriff` mit den Attributen `db.operation` (select/update/...), `db.tabelle` (hergeleitet ohne SQL-Parsing: Angabe beim Aufruf ist nicht noetig — stattdessen `db.sql` mit dem SQL-Text) und `db.system=postgresql` oeffnet. Die Transaktionsverbindung des Transaktionsverwalters wird einmalig gewrappt.

- Alternative A: `io.opentelemetry.instrumentation:opentelemetry-jdbc` (DataSource-Wrapping der Referenz-Instrumentierung) — verworfen: schwergewichtige Zusatz-Abhaengigkeit, unklare Native-Image-Lage, gegen KISS.
- Alternative B: manueller Span pro Repository-Methode — verworfen: ~6 Repositories, viele Methoden, repetitiv und leicht zu vergessen (neue Methode = neue Luecke).
- Der Wrapper sitzt an EINER Stelle und deckt damit auch zukuenftige Repos ab. Der Transaktionsverwalter selbst erhaelt zudem einen eigenen Span `db.transaktion` (Name der Transaktion als Attribut), damit Commit/Rollback sichtbar werden.

### 3. Adapter-Aufrufe als explizite Client-Spans

Die Adapter-Boundaries (`ImperiumZielsystem`, `RebellionZielsystem`) oeffnen um jeden Client-Aufruf (`zustellen`, `statusAbfragen`) einen eigenen Span `adapter.<zielsystem>.<operation>` mit den Attributen `durchlauferhifter.zielsystem` und `durchlauferhitzer.auftrag.id`; Fehler werden ueber `span.recordException` + Status ERROR markiert (fuer `ZustellungUngeklaert`). Dafuer bekommen die Adapter die OpenTelemetry-API als Abhaengigkeit (Quarkus-APIs sind dort bereits etabliert, Camel bleibt tabu; ArchUnit-Regeln unberuehrt).

- Alternative: nur auf automatische Instrumentierung vertrauen — der Rebellion-REST-Client wird von `quarkus-opentelemetry` erfasst, der CXF-SOAP-Client jedoch nicht verlaesslich; explizite Spans sind deterministisch, zielsystem-spezifisch benannt und im Fehlerfall eindeutig.
- REST-Client-Span (Rebellion) und HTTP-Span der OpenSearch-Indexierung entstehen bereits automatisch — sie bleiben als ergaenzende Kind-Spans bestehen.
- Kafka: der Camel-Tracer (Entscheidung 1) erzeugt Producer-/Consumer-Spans; die bestehende manuelle traceparent-Propagierung und Verknuepfung (Relay-Span-Link, Indexier-Span als Kind) bleibt unangetastet und liefert die fachliche Klammer.

### 4. Span-Namen und Attribute konsistent halten

Bestehende Konvention: fachliche Spans kleinbuchstabig-punktiert (`journal.relay`), Attribute `durchlauferhitzer.*` (mit dem historischen Tippfehler `durchlauferhifter.*` bei Metriken — NICHT anfassen). Neue Spans folgen dem Muster: `db.zugriff`, `db.transaktion`, `adapter.<zielsystem>.<operation>`; Camel-Spans behalten ihre camel-typischen Namen (` Camel direct://...`-Stil wird vom Tracer gesetzt und nicht umbenannt).

## Risks / Trade-offs

- [Mehr Spans erhoeht Sperrmuell und Collector-Log-Volume] → Dev-/Playground-Umfeld; falls noetig laesst sich per `quarkus.camel.opentelemetry.exclude-*` steuern (Konfig bleibt KISS: erstmal alles an).
- [JDBC-Proxy: dynamischer Proxy und Native-Image] → Standard-JDK-Mittel (Proxy), keine Reflection-Magie darueber hinaus; native-Kompilierung prueft Aufgabe im `mvn -Pnative`-Smoke-Test sofern ohnehin ausgefuehrt, ansonsten dokumentierter Hinweis.
- [Camel-Tracer doppelt die manuellen Spans (abgleich/journal)] → Gewuenscht: die manuellen Spans sind die fachliche Klammer mit den `durchlauferhitzer.*`-Attributen; Camel-Spans nisten darunter. Duplikate auf Route-Ebene (z. B. `journal-relay` als Route und `journal.relay` als fachlicher Span) sind tolerierbar, da unterschiedlich benannt und unterschiedlicher Aussage.
- [Adapter bekommen eine neue Abhaengigkeit] → nur `opentelemetry-api` (klein, API-only), keine SDK/Export-Artefakte; Layering (adapter -> model) unveraendert.

## Migration Plan

1. `camel-quarkus-opentelemetry` ergaenzen, DB-Wrapper und Adapter-Spans umsetzen — rein additive Aenderungen, kein Verhaltenstransport.
2. `mvn verify` (inkl. ArchUnit) gruen ziehen, danach Dev-Start + e2e und Stichprobe im Collector-Log bzw. Zipkin (Trace-ID aus `traceparent`).
3. Rollback: Abhaengigkeit entfernen, Wrapper und Adapter-Spans entfernen — keine Daten- oder Vertragsaenderung, kein Schema-Migrationsschritt.

## Open Questions

- Ob `quarkus-cxf` den SOAP-Client bereits selbst instrumentiert (dann blieben die expliziten Adapter-Spans trotzdem, aber HTTP-Kind-Spans entfallen gegebenenfalls) — zur Implementierungszeit am Trace-Bild zu pruefen, ohne Auswirkung auf Specs oder Aufgabenstruktur.
