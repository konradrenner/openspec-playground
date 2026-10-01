## Why

Die Beobachtbarkeit und die Schichtung des Service widersprechen den eigenen Architekturzielen: Metriken laufen über Micrometer samt eigener OTLP-Registry-Producer-Bean statt über die OpenTelemetry API, der Anwendungscode loggt über den JBoss-Logger, und die Quarkus-OpenTelemetry-Extension exportiert nur Traces — Logs und Metriken sind in der aktuellen Quarkus-Version standardmäßig deaktiviert. Zusätzlich liegt Persistenz (Agroal-DataSource, JDBC, Transaktionssteuerung) im control statt in der boundary, und die boundary ist nicht nach technischen Belangen gegliedert.

## What Changes

- **Logging:** Der Anwendungscode verwendet ausschließlich `java.util.logging` statt des JBoss-Loggers (8 Klassen im service; die Adapter enthalten kein Logging).
- **OTel-Signale:** Quarkus exportiert künftig alle drei Signale per OTLP an den Collector: Traces (wie bisher, gRPC 4317), Metriken (`quarkus.otel.metrics.enabled=true`, per Default deaktiviert) und Logs (`quarkus.otel.logs.enabled=true`, per Default deaktiviert). Der Collector der devenv-Umgebung hat alle drei Pipelines bereits bereit.
- **Metriken nur über OpenTelemetry:** `quarkus-micrometer`, `micrometer-registry-otlp` und die Producer-Bean `OtlpMetrikRegistry` entfallen; Counter und Gauges (`durchlauferhifter.zustellungen`, `durchlauferhifter.lease.abgelaufen`, `durchlauferhifter.zustellungen.offen`, `durchlauferhifter.zustellungen.fehlgeschlagen`, `durchlauferhifter.outbox.rueckstand`) werden ausschließlich über die OpenTelemetry API erzeugt. Instrument-Namen und Attribute bleiben unverändert.
- **Boundary-Substruktur:** Die boundary des service wird gegliedert in `rest` (JAX-RS), `persistence` (JDBC, Agroal, Transaktion) und `integration` (Camel-Routen, Kafka, OpenSearch); fachliche Gruppen bleiben als Unterpakete erhalten (`integration/journal`, `integration/aufraeumen`).
- **Dependency Inversion:** Das control referenziert keine Infrastruktur und keine boundary-Klassen mehr: Repositories und die Annahme-Transaktion werden Ports (Interfaces) im control, die JDBC-Implementierungen liegen in `boundary/persistence`; die DB-Zählungen der Betriebsmetriken laufen über einen Port. Camel bleibt ausschließlich in `boundary/integration` (ist im control bereits nicht vorhanden und wird künftig per Architekturtest erzwungen).
- **ArchUnit:** Die Architekturtests erzwingen die neue Struktur: control ohne boundary- und Infrastruktur-Referenzen (io.agroal, org.apache.camel, Micrometer), Agroal/JDBC nur in `boundary/persistence`, Camel nur in `boundary/integration`.

Keine fachlichen Breaking Changes: REST-Vertrag, Persistenzverhalten und Meter-Namen bleiben unverändert; es ist ein Refactoring von Infrastruktur und Schichtung.

## Capabilities

### New Capabilities

<!-- keine -->

### Modified Capabilities

- `observability`: Der OTLP-Export MUSS alle drei Signale (Traces, Metriken, Logs) umfassen, Metriken und Logs MÜSSEN explizit aktiviert werden (Quarkus-Default: nur Traces); die Betriebs-Metriken MÜSSEN ausschließlich über die OpenTelemetry API erzeugt werden (kein Micrometer); das Anwendungs-Logging MUSS ausschließlich über `java.util.logging` erfolgen.
- `module-architecture`: Die boundary MUSS in rest, persistence und integration gegliedert sein; das control MUSS über Ports von boundary-Implementierungen und technischer Infrastruktur (Agroal, Camel, Micrometer) entkoppelt sein; Persistenz MUSS in der boundary liegen.
- `build-structure`: Der service DARF weder `quarkus-micrometer` noch Micrometer-Registries deklarieren; die OpenTelemetry-Signale Traces, Metriken und Logs MÜSSEN über `quarkus-opentelemetry` laufen.

## Impact

- **Code (service):** 8 Klassen mit JBoss-Logger; `Betriebsmetriken`, `Zustellungssteuerung`, `Abgleichssteuerung`, `OtlpMetrikRegistry` (Micrometer); `AnnahmeController`, `AuftragRepository`, `ZustellungRepository`, `OutboxRepository`, `JournalRelay`, `JournalOutboxRepository` (Agroal/JDBC im control); boundary-Klassen für das Packaging (rest/persistence/integration); ArchUnit-Tests; `application.properties`; `pom.xml`.
- **Abhängigkeiten:** raus `quarkus-micrometer` und `io.micrometer:micrometer-registry-otlp`; keine neue Abhängigkeit (quarkus-opentelemetry ist vorhanden und bringt die OTLP-Exporter mit).
- **Tests:** Unit-Tests der Metrik-Klassen steigen von Micrometer- auf OTel-API-Testdouble um; die ObservabilityIT verliert ihre Micrometer-In-Prozess-Assertions (Metrik-Verhalten wird unit-seitig abgedeckt, Trace-Assertions bleiben); Repository-/Controller-Tests mocken künftig Ports.
- **Nicht betroffen:** REST-Vertrag, Modell, Adapter-Fachlogik, Collector- und devenv-Konfiguration (alle Pipelines existieren bereits), native-Kompilierbarkeit.
