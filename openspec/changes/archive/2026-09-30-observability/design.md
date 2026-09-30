## Context

Der OTel-Collector der devenv-Umgebung nimmt bereits OTLP auf (gRPC 4317, HTTP 4318; Pipelines traces/metrics/logs mit Debug-Export — Traces landen also im Collector-Log). `TraceKontext` liest heute nur den `traceparent`-Header des Requests (W3C: `00-<32hex traceId>-<16hex spanId>-<flags>`); `auftrag.trace_id` und der Journaleintrag (traceId, traceparent) existieren, sind aber ohne Client-Header leer. Der Journal-Relay versendet ohne Kontext; die Kafka-Nachricht traegt nur den Key. Zustell-/Abgleich-Ergebnisse und der Outbox-Rueckstand sind nicht zaehlbar. Siehe proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- Traces und Metriken ueber OTLP an den Collector; Logs mit Trace-Kontext.
- Trace-Kontext immer in auftrag/Journal gespeichert (aktiver Span, auch ohne Client-Trace).
- Relay-Span mit Span-Link auf den gespeicherten traceparent; Kontext via Kafka-Header bis zur Indexierung.
- Span-Attribute auftrag.id/zielsystem/zustellstatus; Counter und Gauges wie im Spec.
- README mit curl-Nachfahrt fuer alle WireMock-Faelle und Collector-Log-Hinweis.

**Non-Goals:**

- Kein Metrics-Scrape-Endpoint (nur OTLP-Export; MeterRegistry intern testbar).
- Keine Histogramme/Timer, keine Alarme, kein Dashboard.
- Keine Instrumentierung der Adapter-Module (Quarkus-REST/SOAP-Client-Spans entstehen automatisch im Service).
- Kein OTLP-Log-Export, sofern die Quarkus-Version ihn nicht bietet (siehe D1).

## Decisions

### D1: Telemetrie-Stack und Exporter

- Traces: `quarkus-opentelemetry` — instrumentiert JAX-RS (Server-Span pro Request) und REST-Client; OTLP-Exporter per Default auf `http://localhost:4317` (passt zum Collector; per `quarkus.otel.exporter.otlp.traces.endpoint` konfigurierbar). Kein `camel-quarkus-opentelemetry` — die Camel-Routen sind duenn und die Spans werden gezielt in den control-Klassen gesetzt (explizit statt Auto-Instrumentation, KISS).
- Metriken: `quarkus-micrometer` + `micrometer-registry-otlp` — Export nach `http://localhost:4318/v1/metrics` (Collector OTLP HTTP); `micrometer-registry-prometheus` bewusst weg — kein Scrape-Endpoint.
- Logs: `quarkus-opentelemetry` reichert die JBoss-LogManager-Zeilen mit traceId/spanId an (Konsolen-Format der Extension). Ein vollstaendiger OTLP-Log-Export ist in dieser Quarkus-Version nicht verfuegbar — dokumentierte Einschraenkung: Logs laufen mit Trace-Kontext in die Konsole, die Traces ins Collector-Log. Wird beim Implementieren gegengeprueft; falls die Version doch einen Log-Exporter bietet, wird er aktiviert (ein Konfigurationsschalter, kein Strukturwandel).
- Test-Profil: Exporter in Tests deaktiviert (`quarkus.otel.enabled=false` + Micrometer-OTLP-Registry aus), damit die ITs unabhaengig vom Collector laufen; MeterRegistry bleibt als Bean messbar.

### D2: Trace-Kontext-Speicherung (immer gesetzt)

- `TraceKontext` (control) liefert ab sofort: traceId aus `Span.current()` (Quarkus-OTel), wenn kein aktiv-NOOP-Span; Rueckfalle auf den gepaarten Header (bestehende Logik fuer explizite Tests mit festen Trace-IDs). Rueckgabe zusaetzlich des aktuellen traceparent-Strings (`00-<traceId>-<spanId>-<flags>`), damit der Journaleintrag einen echten, abfragbaren Kontext speichert.
- `AnnahmeController` nutzt beide Werte (auftrag.trace_id, Journaleintrag traceId/traceparent) — kein Schema-Bruch, keine Migration.

### D3: Relay-Span, Span-Link und Kafka-Header-Propagierung

- `JournalRelay` oeffnet pro Journaleintrag einen Span (`journal.relay`), via `OpenTelemetry`/`Tracer` (CDI-Injektion aus der Quarkus-Extension): `tracer.spanBuilder("journal.relay")` — mit `addLink(SpanContext)` aus dem gespeicherten `traceparent` des Payloads (W3C-Parsing: traceId 32 hex, spanId 16 hex — Hilfsklasse, unit-getestet; ungueltiger gespeicherter Kontext fuehrt zu keinem Link, versendet aber normal).
- `CamelJournalVersand` setzt zusaetzlich zum Kafka-Key die W3C-Header: `traceparent` (aktueller Relay-Span) — Kafka als Text-Header, damit der Consumer ohne Spezialdeserialisierer liest.
- `JournalConsumerRoute` baut vor dem Indexieren einen Span (`journal.indexieren`), dessen Eltern-Kontext aus dem Kafka-Header `traceparent` gelesen wird (gleiche Parsing-Hilfe); fehlt/ungueltig der Header, laeuft der Span root-los weiter. Der manuelle Kafka-Commit bleibt von der Span-Verwaltung unberuehrt (Commit weiterhin erst nach erfolgreicher Indexierung).

### D4: Span-Attribute

- `Zustellungssteuerung` und `Abgleichssteuerung` setzen auf dem aktuellen Span `durchlauferhifter.auftrag.id` (immer), `durchlauferhifter.zielsystem` (immer) und `durchlauferhifter.zustellstatus` (soweit bekannt: Ausgangsstatus beim Abgleich, Ergebnis beim Verbuchen) — als kleine Hilfsklasse `SpanAttribute` (control), die auf NOOP-Spans (Tests ohne OTel) harmlos no-op ist.

### D5: Metriken (Micrometer)

- Counter `durchlauferhifter.zustellungen` (Tags `zielsystem`, `ergebnis`): erhoeht in `Zustellungssteuerung` (Erstzustellung BESTAETIGT/UNGEKLAERT) und `Abgleichssteuerung` (alle Abgleichsergebnisse inkl. Neuversand und FEHLGESCHLAGEN) — genau an den Stellen, an denen auch verbucht wird.
- Counter `durchlauferhifter.lease.abgelaufen` (Tag `ausgangsstatus`): erhoeht in der `Abgleichssteuerung`, wenn die beanspruchte Zustellung aus einem Lease-Ausfall stammt — dafuer traegt `Beanspruchung` kuenftig den Ausgangsstatus (das BeanSpruchungs-Statement liefert ihn bereits aus dem frei-CTE); Zaehlung nur bei IN_ZUSTELLUNG/IN_ABGLEICH, nicht bei faelligem UNGEKLAERT.
- Gauges: neue `Betriebsmetriken`-Bean (control, plain JDBC wie die Repositories) mit `MeterRegistry`: `durchlauferhifter.zustellungen.offen` (status IN (IN_ZUSTELLUNG, IN_ABGLEICH, UNGEKLAERT)), `.fehlgeschlagen` (status = FEHLGESCHLAGEN), `durchlauferhifter.outbox.rueckstand` (gesendet_am IS NULL) — als `Gauge.builder` mit Supplier, gezaehlt bei Abfrage (kein Poll-Thread, KISS).
- Unit-Tests messen an einer `SimpleMeterRegistry` (kein QuarkusTest noetig); die IT prueft Meter-Staende nach einer Annahme.

### D6: README

- `README.md` (Wurzel): Dienste starten (`devenv up -d`), Service starten (`mvn -pl raumschiffwerft-service quarkus:dev` bzw. test-jar), curl-Beispiele fuer alle WireMock-Faelle (Imperium Bestellung Erfolg/Jar Jar/Langsam, Statusabfragen, Rebellion Beschaffung Erfolg/Jar Jar/Langsam, Status 404/IN_ARBEIT/ERLEDIGT, health, ping) — als direkte Aufrufe gegen `localhost:8089` zum Nachfahren der Stubs — plus Annahme/Abfrage gegen den Service (`POST /api/v1/kaufauftraege` mit `Idempotency-Key` und `traceparent`, GET), und der Hinweis, dass die Traces im `otelcol`-Prozesslog erscheinen (`devenv processes logs otelcol` bzw. Debug-Export) und traceId-basiert gesucht werden.

### D7: Teststrategie

- Unit-Tests (kein `@QuarkusTest`): `SimpleMeterRegistry` fuer Counter-Zaehlungen in `Zustellungssteuerung`/`Abgleichssteuerung` (Ergebnis-Tags, Lease-Counter nur bei Lease-Uebernahme), `Betriebsmetriken`-Gauge-Registrierung (Registry mit Mock-DataSource oder Grenzfaelle), W3C-Parsing-Hilfe (gueltig/ungueltig,.traceparent-Roundtrip), Span-Attribute-Hilfe (NOOP-span-tauglich).
- ITs: bestehende bleiben unberuehrt (OTel in Tests deaktiviert, `TraceKontext`-Header-Fallback erhaelt die JournalRelayIT mit fester Trace-ID). Neue `ObservabilityIT`: POST ohne traceparent → `auftrag.trace_id` gesetzt; MeterRegistry nach Annahme pruefen (Counter ergebnis=BESTAETIGT mit zielsystem-Tag); Gauge-Werte plausibel.
- Collector-Integration (Traces im Collector-Log) ist manuell nachgefahren — README beschreibt den Weg; ein automatisiertes Collector-Log-Parsing ist bewusst kein Test (fragil, KISS).

## Risks / Trade-offs

- [OTLP-Log-Export vermutlich nicht verfuegbar] → D1 haelt den Umweg (Konsole mit traceId) dokumentiert; ein Upgrade ist ein Konfigurationsschalter.
- [Span-Link nur bei gueltigem gespeicherten traceparent] → ungueltige/fehlende Kontexte versenden normal ohne Link (kein Datenverlust); Parsing-Hilfe ist unit-getestet.
- [Micrometer-OTLP-Registry-Konfiguration je nach Quarkus-Version unterschiedlich] → D1 nennt Ziel und Default; exakte Property-Namen werden beim Implementieren festgezurrt (Muster wie OpenFeature-Beta im Change annahme-und-zustellung).
- [Gauges zaehlen bei Abfrage] → bewusst kein Hintergrund-Polling; der Rueckstand ist eine Momentaufnahme (KISS).
- [Counter inkrementiert vor verbuchen (DB kann fehlschlagen)] → gezaehlt wird am Verbuchungsort (nach dem Update, nicht beim Versuch) — Counter entspricht damit den tatsaechlichen Verbuchungen.

## Migration Plan

Rein additiv: keine Migration, keine Daten- oder Vertragsaenderung. Ausrollen = Deploy; Exporter greifen sofort (Collector laeuft). Rollback = Deploy des alten Stands — Collector bleibt unberuehrt, fehlende Telemetrie ist kein Datenverlust.

## Umsetzungskorrekturen (Apply-Phase)

- **Micrometer-OTLP**: `quarkus.micrometer.export.otlp.*` existiert in dieser Quarkus-Version nicht (unrecognized property). Die Registry wird stattdessen als CDI-Bean produziert (`OtlpMetrikRegistry`, Klasse `io.micrometer.registry.otlp.OtlpMeterRegistry`, URL aus `durchlauferhitzer.metrik.otlp.url`); quarkus-micrometer nimmt MeterRegistry-Beans automatisch in den Composite auf. Ohne dieses Bean waere der Composite kinderlos und wuerde Zaehler verwerfen.
- **Gauges**: `Betriebsmetriken` registriert die Gauges per `StartupEvent`-Observer statt `@PostConstruct` — ohne Observer wuerde die Bean nie instanziiert und die Gauges nie registriert.
- **OTel-SDK 1.57**: `SpanProcessor.onEnd` ist void, `onStart(Context, ReadWriteSpan)`; ein eigener Provider wird per `OpenTelemetrySdk.builder().setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(...).build())` gesetzt. Der Indexier-Span uebernimmt den Kafka-Kontext per `setParent(Context.root().with(Span.wrap(kontext)))`.
- **OTLP-Log-Export**: bestaetigt nicht verfuegbar in dieser Quarkus-Version — die dokumentierte Einschraenkung bleibt (Logs mit traceId in der Konsole, Traces und Metriken via OTLP; Collector-Logs-Pipeline ist fuer spaetere Verfuegungen bereit).
- **Metrik-Namen**: einheitlich `durchlauferhitzer.*` (ein paar Meter waren in der Umsetzung zunaechst als `durchlauferhifter.*` registriert und sind korrigiert).
