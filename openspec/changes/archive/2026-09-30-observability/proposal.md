## Why

Der Service ist fachlich fertig, aber im Betrieb blind: Es gibt keine Traces ueber die gesamte Kette (Annahme → Zustellung/Abgleich → Journal → Indexierung), keine Metriken ueber Zustell-Ergebnisse, abgelaufene Leases und Aufstauungen, und die Trace-IDs in der Datenbank stammen bislang nur dann aus einem echten Trace, wenn der Client einen `traceparent`-Header mitschickt. Der Change macht die Raumschiffwerft beobachtbar — gegen den in devenv bereits laufenden OTel-Collector.

## What Changes

- **OpenTelemetry aktivieren (OTLP)**: Traces ueber `quarkus-opentelemetry` an den Collector (OTLP gRPC 4317); Metriken ueber `quarkus-micrometer` mit OTLP-Registry (Collector OTLP HTTP 4318). Logs erhalten den Trace-Kontext (traceId) in jeder Zeile; ein vollstaendiger OTLP-Log-Export ist in dieser Quarkus-Version nicht verfuegbar und wird als dokumentierte Einschraenkung im Design festgehalten (Collector-Log zeigt die Traces).
- **Trace-Kontext in auftrag und Journaleintrag**: Die Trace-ID MUSS immer gespeichert werden — aus dem aktiven Span der Annahme; der `traceparent`-Header des Clients ist nur noch Kontext-Quelle fuer die eingehende Verknuepfung. Ohne Client-Trace startet die Annahme einen eigenen Span (trace_id in `auftrag` und im Journaleintrag gesetzt).
- **Relay verknuepft und propagiert**: Der Journal-Relay oeffnet pro Journaleintrag einen Span, verknuepft ihn per Span-Link mit dem gespeicherten `traceparent` und propagiert seinen Kontext ueber Kafka-Header bis zur Indexierung — der Indexier-Span ist Kind des Relay-Spans und verwandt mit dem urspruenglichen Annahme-Span.
- **Span-Attribute**: `durchlauferhitzer.auftrag.id`, `durchlauferhifter.zielsystem` und `durchlauferhifter.zustellstatus` auf den Zustell- und Abgleich-Spans.
- **Metriken**: Counter `durchlauferhitzer.zustellungen` (Tags zielsystem, ergebnis) fuer jedes Zustell- bzw. Abgleichsergebnis; Counter `durchlauferhifter.lease.abgelaufen` (Tag ausgangsstatus) fuer nach Absturz uebernommene Zustellungen; Gauges `durchlauferhifter.zustellungen.offen`, `durchlauferhifter.zustellungen.fehlgeschlagen` und `durchlauferhifter.outbox.rueckstand` (ungesendete Outbox-Zeilen).
- **README**: curl-Beispiele fuer alle WireMock-Faelle (Bestellung/Status je Imperium und Rebellion, Fehler- und Timeout-Stubs, Health/Ping), Annahme- und Abfrage-Aufrufe gegen den Service sowie der Hinweis, wo die Traces im Collector-Log erscheinen.

Keine Aenderungen an REST-Vertrag, Tabellenformat, Zustell-/Abgleich-/Journal-Ablaeufen oder Modell/Adaptern.

## Capabilities

### New Capabilities

- `observability`: Vertrag der Beobachtbarkeit — OTel-Telemetrie ueber OTLP, Trace-Kontext-Pflege in auftrag/Journal, Relay-Span-Link und Kafka-Kontext-Propagierung bis zur Indexierung, Span-Attribute sowie die Meter (Counter und Gauges).

### Modified Capabilities

Keine — alle bestehenden Verhaltensanforderungen bleiben unberuehrt; die Telemetrie ist additiv.

## Impact

- **Abhaengigkeiten (Service-POM)**: `quarkus-opentelemetry` (Traces, OTLP), `quarkus-micrometer` + `micrometer-registry-otlp` (Metriken, OTLP HTTP). Versionen aus der Quarkus-Plattform.
- **Code (nur `raumschiffwerft-service`)**:
  - `control`: `TraceKontext` liest den aktiven Span (Fallback Header), `AnnahmeController` speichert dessen Trace-ID/traceparent; `Zustellungssteuerung`/`Abgleichssteuerung` zaehlen Ergebnisse und Leases, setzen Span-Attribute; `JournalRelay` oeffnet pro Eintrag einen Span mit Link auf den gespeicherten traceparent; neue `Betriebsmetriken` (Gauges) mit JDBC-Zaehlungen; `Beanspruchung` erhaelt den Ausgangsstatus (Basis fuer den Lease-Counter).
  - `boundary`: Kafka-Header-Propagierung im `CamelJournalVersand` (traceparent des Relay-Spans als Nachricht-Header), Kontext-Aufnahme in der `JournalConsumerRoute`, Konfiguration der Exporter.
- **Konfiguration**: OTel-Exporter-Endpunkte (defaults passen zur devenv-Collector: 4317 gRPC Traces, 4318 HTTP Metriken); keine devenv-Aenderung.
- **Tests**: Unit-Tests mit `SimpleMeterRegistry` (Zaehler-Stand, Lease-Counter, Gauge-Registrierung) und fuer die Span-Kontext-Hilfen; IT: Trace-ID in `auftrag` auch ohne Client-Traceparent, Meter-Staende nach einer Annahme, weiterhin gruen auf allen bestehenden ITs.
- **Nicht betroffen**: Datenbank-Schema (keine Migration), WireMock-Stubs, REST-Vertrag, devenv-Dienste (Collector, Kafka, OpenSearch laufen bereits).
- **Betrieb**: Nach Deploy erscheinen Traces im Collector-Debug-Log, Metriken im Collector (Pipelines existieren bereits); die README dokumentiert die curl-Nachfahrt.
