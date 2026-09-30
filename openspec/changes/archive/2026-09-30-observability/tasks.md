## 1. Abhaengigkeiten und Exporter-Konfiguration

- [x] 1.1 Service-POM erweitern: `quarkus-opentelemetry` (Traces), `quarkus-micrometer` + `micrometer-registry-otlp` (Metriken) — verifiziert durch `mvn -pl raumschiffwerft-service -am compile`
- [x] 1.2 Exporter konfigurieren (Traces OTLP gRPC Default 4317, Metriken OTLP HTTP 4318; exakte Properties gegen die Quarkus-Version festziehen) und im Test-Profil deaktivieren — verifiziert durch laufende bestehende ITs ohne Collector-Abhaengigkeit
- [x] 1.3 Gegenpruefen, ob die Quarkus-Version einen OTLP-Log-Export bietet; falls ja aktivieren, sonst die dokumentierte Einschraenkung in design.md abschliessen — verifiziert durch Collector-Log bzw. Konsole mit traceId

## 2. Trace-Kontext in auftrag und Journaleintrag

- [x] 2.1 `TraceKontext` erweitern: Trace-ID und traceparent-String aus dem aktiven Span, Fallback auf den Header — verifiziert durch Unit-Tests (mit und ohne aktiven Span, ungueltiger Header)
- [x] 2.2 `AnnahmeController` speichert trace_id/traceparent aus `TraceKontext` — verifiziert durch die `ObservabilityIT` (Auftrag ohne Client-Traceparent hat trace_id) und die bestehende `JournalRelayIT` (feste Trace-ID)

## 3. Relay-Span, Span-Link und Propagierung

- [x] 3.1 W3C-Hilfsklasse (traceparent ↔ SpanContext parsen/formatieren) in control — verifiziert durch Unit-Tests (gueltig, ungueltig, Rundlauf)
- [x] 3.2 `JournalRelay` oeffnet pro Eintrag einen Span mit Span-Link auf den gespeicherten traceparent; `CamelJournalVersand` setzt den traceparent-Header der Kafka-Nachricht — verifiziert durch Unit-Test (Link gesetzt bei gueltigem, ohne bei ungueltigem Kontext) und `ObservabilityIT`
- [x] 3.3 `JournalConsumerRoute` startet den Indexier-Span aus dem Kafka-Header-Kontext — verifiziert durch Unit-Test (Header-Kontext wird Eltern-Span; ohne Header root-los) und `ObservabilityIT`

## 4. Span-Attribute

- [x] 4.1 Hilfsklasse `SpanAttribute` und Attribut-Setzung in `Zustellungssteuerung` (auftrag.id, zielsystem, ergebnis) und `Abgleichssteuerung` (auftrag.id, zielsystem, ausgangsstatus) — verifiziert durch Unit-Tests (Attribute gesetzt, NOOP-Span-fest)

## 5. Betriebs-Metriken

- [x] 5.1 Counter `durchlauferhifter.zustellungen` (zielsystem, ergebnis) in `Zustellungssteuerung` und `Abgleichssteuerung` — verifiziert durch Unit-Tests mit `SimpleMeterRegistry` (Ergebnis- und Zielsystem-Tags, Zaehlung je Verbuchung)
- [x] 5.2 `Beanspruchung` um den Ausgangsstatus erweitern (Statement liefert ihn bereits) und Counter `durchlauferhifter.lease.abgelaufen` (ausgangsstatus) in der `Abgleichssteuerung` — verifiziert durch Unit-Tests (Zaehlung nur bei IN_ZUSTELLUNG/IN_ABGLEICH)
- [x] 5.3 `Betriebsmetriken`-Bean mit den Gauges offen/fehlgeschlagen/outbox.rueckstand (JDBC-Zaehlung bei Abfrage) — verifiziert durch Unit-Tests (Registrierung, Supplier-Werte) und die `ObservabilityIT`

## 6. Integrationstest und README

- [x] 6.1 `ObservabilityIT`: POST ohne traceparent → `auftrag.trace_id` gesetzt; MeterRegistry nach Annahme geprueft (Counter ergebnis=BESTAETIGT mit zielsystem-Tag, Gauges registriert) — verifiziert durch `mvn -pl raumschiffwerft-service verify` (Failsafe)
- [x] 6.2 README: Dienste/Service-Start, curl-Beispiele fuer alle WireMock-Faelle (Imperium/Rebellion je Bestellung/Beschaffung Erfolg/Jar Jar/Langsam, Statusabfragen inkl. 404/IN_ARBEIT/ERLEDIGT/ABGESCHLOSSEN, health, ping), Service-Annahme mit Idempotency-Key und traceparent, GET — verifiziert durch Ausfuehrung der curl-Beispiele gegen die laufenden devenv-Dienste

## 7. Gesamtsicherung

- [x] 7.1 `mvn verify` im Wurzelverzeichnis (devenv-Shell): Unit-, Architektur- und Integrationstests aller vier Module — verifiziert durch gruenen Build
- [x] 7.2 Specs syncen und Change archivieren (`openspec`-Workflow); Haupt-Spec `observability` (neu) anlegen — verifiziert durch `openspec validate` und `openspec status`
