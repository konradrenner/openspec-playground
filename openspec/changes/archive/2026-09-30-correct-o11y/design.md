## Context

Siehe proposal.md für Motivation und Ist-Analyse. Rahmenbedingungen: Quarkus 3.33 (Platform `3.33.3.3`), `quarkus-opentelemetry` ist bereits deklariert und exportiert nur Traces per Default — Metriken und Logs sind in dieser Quarkus-Version standardmäßig deaktiviert und MÜSSEN per `quarkus.otel.metrics.enabled=true` bzw. `quarkus.otel.logs.enabled=true` aktiviert werden; die eingebauten OTLP-Exporter werden per CDI verdrahtet. Der OTel-Collector der devenv-Umgebung besitzt bereits Pipelines für Traces, Metriken und Logs (je Receiver otlp, Debug-Exporter). Der Anwendungscode nutzt aktuell den JBoss-Logger (8 Klassen), Micrometer (Counter in `Zustellungssteuerung` und `Abgleichssteuerung`, Gauges in `Betriebsmetriken`, OTLP-Producer `OtlpMetrikRegistry`) und Agroal/JDBC im control (`AnnahmeController` mit eigener Transaktionssteuerung, `AuftragRepository`, `ZustellungRepository`, `OutboxRepository`, `JournalRelay`, `JournalOutboxRepository`), das Repository `AufraeumRepository` liegt bereits in der boundary.

## Goals / Non-Goals

**Goals:**

- Einheitliche Telemetrie: alle drei Signale über `quarkus-opentelemetry` per OTLP an den Collector; Metriken ausschließlich über die OpenTelemetry API; Logging ausschließlich über `java.util.logging`.
- Schichtung: Persistenz (inkl. Transaktion) in `boundary/persistence`, Camel/Kafka/OpenSearch in `boundary/integration`, REST in `boundary/rest`; control nur noch mit Ports.
- Durchsetzung per Architekturtest, sodass Rückfälle den Build scheitern lassen.

**Non-Goals:**

- Keine Änderung an Fachverhalten, REST-Vertrag, Datenmodell, Instrument-Namen oder Collect- or-Konfiguration.
- Keine Einführung von JTA/Narayana — es bleibt bei plain JDBC (KISS, keine neue Abhängigkeit).
- Kein Umbau der Adapter oder des Modells (Adapter enthalten kein Logging, keine Metriken).
- Keine Korrektur der bestehenden Instrument-/Attribut-Namen (z. B. `durchlauferhifter.*`) — Namensstabilität geht vor Kosmetik.

## Decisions

### 1. java.util.logging als einzige Logging-API

Alle 8 Klassen (boundary + control) stellen von `org.jboss.logging.Logger` auf `java.util.logging.Logger` um (`Logger.getLogger(Klasse.class.getName())`, `LOG.log(Level.WARNING, "...", e)` statt `warnf`). Quarkus setzt `java.util.logging.manager=org.jboss.logmanager.LogManager` (bereits im Failsafe-Setup konfiguriert), sodass JUL-Records über den JBoss-LogManager laufen und der OpenTelemetry-Log-Handler (`quarkus.otel.logs.enabled=true`) sie mit Trace-Kontext per OTLP exportiert. JUL-Level (INFO/WARNING/SEVERE) genügen dem bestehenden Warn-/Fehler-Loggen.

- Alternative: bei JBoss-Logger bleiben — verworfen (Vorgabe); Quarkus-interne Logger sind davon unberührt.

### 2. OTel-Konfiguration in application.properties

Ergänzt werden `quarkus.otel.metrics.enabled=true`, `quarkus.otel.logs.enabled=true` sowie die Endpunkte `quarkus.otel.exporter.otlp.metrics.endpoint` (OTLP HTTP, Default `http://localhost:4318`) und `quarkus.otel.exporter.otlp.logs.endpoint` (OTLP gRPC, `http://localhost:4317`); Traces bleiben wie bisher. Die bisherige Property `durchlauferhitzer.metrik.otlp.url` entfällt mit `OtlpMetrikRegistry`. Der Export geschieht über die eingebauten, per CDI verdrahteten OTLP-Exporter (Default `quarkus.otel.*.exporter=cdi`).

### 3. Metriken ausschließlich über die OpenTelemetry API

`Betriebsmetriken`, `Zustellungssteuerung` und `Abgleichssteuerung` injizieren sich `MeterProvider` (bereitgestellt von `quarkus-opentelemetry`; beziehen via `OpenTelemetry#getMeterProvider`-CDI-Bean oder direkter `MeterProvider`-Injektion) und erzeugen:

- Counter `durchlauferhifter.zustellungen` / `durchlauferhifter.lease.abgelaufen` als `LongCounter` mit `Attributes` (zielsystem/ergebnis bzw. ausgangsstatus),
- Gauges als `ObservableLongGauge` mit Callback — die Zählung passiert weiterhin erst bei Abfrage (kein Poll-Thread, wie bisher).

`OtlpMetrikRegistry`, `quarkus-micrometer` und `micrometer-registry-otlp` werden gelöscht. Kein Micrometer im Classpath.

- Alternative: Micrometer-OTLP behalten und nur die OTel-Aktivierung ergänzen — verworfen (Vorgabe: ausschließlich OpenTelemetry API).

### 4. Ports im control, JDBC in boundary/persistence (Dependency Inversion)

- `AuftragRepository`, `ZustellungRepository`, `OutboxRepository`, `JournalOutboxRepository` werden zu Interfaces im control (gleiche Methoden OHNE `Connection`-Parameter); die JDBC-Implementierungen (z. B. `JdbcAuftragRepository`) wandern nach `boundary/persistence`.
- Die Annahme-Transaktion (`AnnahmeController`: `getConnection`, `setAutoCommit(false)`, `commit`/`rollback`) wird ein Port `Transaktionsverwalter` im control (etwa `<T> T inTransaktion(Arbeit<T> arbeit)`); die Implementierung in `boundary/persistence` öffnet die Verbindung, führt Commit/Rollback aus und stellt sie den Repository-Implementierungen über einen paketprivaten Verbindungshalter (ThreadLocal) innerhalb desselben Pakets bereit. Das control bleibt JDBC-frei.
- `Betriebsmetriken` bekommt einen Port für die DB-Zählungen (z. B. `Bestandszaehler` mit den drei Zähl-Operationen); die JDBC-Implementierung liegt in `boundary/persistence`. Die OTel-Instrumente bleiben im control — sie nutzen nur die OTel API.
- `JournalRelay` (control/journal) gibt seine Outbox-Lese-/Markier-Logik an den Port `JournalOutboxRepository` ab (Implementierung in `boundary/persistence`); Relay-Logik, Spans und Links bleiben im control.

Alternativen: JTA/`@Transactional` (verworfen: neue Abhängigkeit, plain JDBC ist bewusst gewählt); `Connection` als Parameter durch die Ports reichen (verworfen: leakt JDBC in das control).

### 5. Boundary-Substruktur

- `boundary/rest`: `KaufauftraegeResource`, `UngueltigeAnfrage` (und ein eventueller Exception-Mapper).
- `boundary/persistence`: JDBC-Repository-Implementierungen, `Transaktionsverwalter`-Implementierung, Verbindungshalter, `AufraeumRepository`.
- `boundary/integration`: Camel-Routen und -Ports (`ZustellRoute`, `CamelZustellport`, `AbgleichRoute`, `CamelAbgleichsport`, `AufraeumRoute`), fachlich gruppiert als `integration/journal` (`CamelJournalVersand`, `JournalRelayRoute`, `JournalConsumerRoute`, `JournalIndexVerwaltung`, `OpenSearchIndexClient`, `OpenSearchJournalIndex`) und `integration/aufraeumen`.
- Die Komponentenregel (`ComponentArchitectureTest`, Muster `..service..journal..`) bleibt funktionsfähig: control/journal bleibt bestehen, boundary-seitig liegt das Journal unter `integration/journal`.

### 6. Architekturtests erzwingen die Regeln

`BoundaryControlEntityArchitectureTest` und ein neuer/erweiterter Regelkatalog ergänzen: control ohne Referenzen auf `io.agroal..`, `org.apache.camel..`, `java.sql..`/`javax.sql..` und boundary-Pakete; Agroal/JDBC ausschließlich in `boundary/persistence`; Camel ausschließlich in `boundary/integration`; im Anwendungscode kein `org.jboss.logging..` und kein `io.micrometer..`. Verstöße lassen den Build scheitern (Spec: module-architecture).

### 7. Teststrategie ohne Micrometer

- Unit-Tests (`*Test`, ohne QuarkusTest) der Metrik-Klassen ersetzen die Micrometer-`MeterRegistry` durch handgeschriebene Testdoubles der OpenTelemetry-API-Interfaces (`MeterProvider`/`Meter`/`LongCounter`/`ObservableLongGauge`-Minimalimplementierungen), die Aufrufe und Callback-Messungen aufzeichnen. Kein SDK nötig, konventionskonform frameworkfrei.
- Die `ObservabilityIT` verliert ihre Micrometer-In-Prozess-Assertions (Gauges über `meterRegistry.get(...)`); die Trace-Assertions (trace_id in DB, Log-Kontext) bleiben. Das Metrik-Verhalten (Counter-Anstieg, Gauge-Werte) ist unit-seitig abgedeckt; die Collector-Sichtbarkeit ist über den Debug-Exporter der devenv-Umgebung gegeben.
- Repository-/Controller-Tests mocken künftig die Ports; die JDBC-Implementierungen werden weiterhin über die bestehenden ITs gegen die devenv-Postgres abgesichert.

## Risks / Trade-offs

- [OTel-Logs und -Metriken sind in Quarkus noch relativ jung] → Version ist im Parent-POM zentral gebunden; die Aktivierung erfolgt nur über Konfigurationsproperties, kein eigener Exporter-Code; Verhalten wird über die ObservabilityIT (Logs/Trace-Kontext) und den Collector-Debug-Export geprüft.
- [Gauge-Callback-Messungen erfolgen im OTel-Exportintervall statt wie bisher bei Micrometer-Schritten] → Semantik identisch (Abfrage-Zeitpunkt-Zählung, kein Poll-Thread), nur das zeitliche Raster des Exports ändert sich.
- [JUL-Formatierung unterscheidet sich vom JBoss-Logger (kein printf-Style `warnf`)] → Meldungen werden auf `LOG.log(Level, msg, args/throwable)` umgestellt; Platzhalter-Logik entfällt oder wird per `String.format` gelöst.
- [ThreadLocal-Verbindungshalter ist ein bewusstes Infrastrukturdetail] → vollständig in `boundary/persistence` gekapselt und nur dort sichtbar; Fehlerpfad (Rollback, `DatenbankNichtErreichbar`) bleibt wie bisher.
- [Große Anzahl verschobener Klassen kann Tests brechen, die alte Pakete referenzieren] → Moves als reine Umbenennung mit anschließendem `mvn verify` als Sicherungsnetz; keine Fachlogik-Änderung in diesem Schritt.

## Migration Plan

Reiner Code- und Konfigurationsumbau, keine Datenmigration. Reihenfolge: (1) Konfiguration + Micrometer-Entfernung + OTel-Umbau der Instrumente, (2) JUL-Umstellung, (3) Ports/Persistence-Move mit Boundary-Substruktur, (4) Architekturregeln, (5) `mvn verify` über alle Module gegen die devenv-Infrastruktur. Rollback per Revert; es entstehen keine persistenten Datenstrukturen oder externen Verträge, die von der Änderung abhängen.
