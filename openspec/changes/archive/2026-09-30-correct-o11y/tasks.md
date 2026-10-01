## 1. OTel-Aktivierung und Micrometer-Entfernung

- [x] 1.1 In `raumschiffwerft-service/src/main/resources/application.properties` die Signale aktivieren und Endpunkte setzen: `quarkus.otel.metrics.enabled=true`, `quarkus.otel.logs.enabled=true`, `quarkus.otel.exporter.otlp.metrics.endpoint=http://localhost:4318`, `quarkus.otel.exporter.otlp.logs.endpoint=http://localhost:4317`; `durchlauferhitzer.metrik.otlp.url` entfernen. Verifikation: Anwendung startet (IT) und Collector-Debug-Export zeigt Metrik- und Log-Einträge
- [x] 1.2 Aus `raumschiffwerft-service/pom.xml` `quarkus-micrometer` und `io.micrometer:micrometer-registry-otlp` entfernen; `OtlpMetrikRegistry.java` löschen. Verifikation: `mvn -pl raumschiffwerft-service dependency:tree` enthaelt keine Micrometer-Artefakte, Build kompiliert (Klassen aus 1.3 sind umgestellt)

## 2. Metriken ausschließlich per OpenTelemetry API

- [x] 2.1 `Betriebsmetriken` auf OTel API umstellen: `MeterProvider`-Injektion, drei `ObservableLongGauge` (`durchlauferhifter.zustellungen.offen`, `.fehlgeschlagen`, `durchlauferhifter.outbox.rueckstand`) mit Callback; DB-Zählungen über neuen Port `Bestandszaehler` im control (Interface mit den drei Zähl-Operationen). Verifikation: Unit-Test mit OTel-Testdouble misst die Gauge-Werte
- [x] 2.2 `Zustellungssteuerung` und `Abgleichssteuerung` auf OTel API umstellen: `LongCounter durchlauferhifter.zustellungen` (Attributes zielsystem/ergebnis) und `LongCounter durchlauferhifter.lease.abgelaufen` (Attribute ausgangsstatus) statt Micrometer-`Counter`. Verifikation: `ZustellungssteuerungTest` und `AbgleichssteuerungTest` gruen mit OTel-Testdoubles (Counter-Aufrufe mit Attributen assertiert)
- [x] 2.3 OTel-API-Testdouble für Unit-Tests anlegen (Mindestimplementierungen von MeterProvider/Meter/LongCounter/ObservableLongGauge, die Aufrufe und Callbacks aufzeichnen; ohne SDK, ohne QuarkusTest). Verifikation: `mvn -pl raumschiffwerft-service test` gruen, kein Micrometer-Import in Test-Klassen
- [x] 2.4 `ObservabilityIT` von Micrometer befreien: MeterRegistry-Importe und Gauge-Assertions entfernen, Trace-/Log-Assertions (trace_id in DB, Trace-Kontext) beibehalten bzw. um Log-Pipeline-Pruefung ergaenzen. Verifikation: IT laeuft gegen devenv-Infrastruktur gruen

## 3. Logging per java.util.logging

- [x] 3.1 Alle 8 Klassen von `org.jboss.logging.Logger` auf `java.util.logging.Logger` umstellen (`Zielsystemwahl`, `Zustellungssteuerung`, `Abgleichssteuerung`, `Betriebsmetriken`, `JournalRelay`, `AufraeumSteuerung`, `AufraeumRepository`, `JournalIndexVerwaltung`): `warnf`/`errorf` durch `LOG.log(Level.WARNING/SEVERE, msg, throwable/args)` ersetzen. Verifikation: `grep -r "org.jboss.logging" raumschiffwerft-service/src/main` liefert keine Treffer, Build kompiliert
- [x] 3.2 Logausgaben des laufenden Service pruefen: JUL-Zeilen erscheinen auf Konsole und — nach Aktivierung aus 1.1 — mit Trace-Kontext in der Log-Pipeline des Collectors. Verifikation: Annahme-Request in der IT, Collector-Debug-Log zeigt Logeinträge mit Trace-ID

## 4. Boundary-Substruktur und Dependency Inversion

- [x] 4.1 Ports im control anlegen: `AuftragRepository`, `ZustellungRepository`, `OutboxRepository`, `JournalOutboxRepository` zu Interfaces umwandeln (Methoden ohne `java.sql.Connection`-Parameter, SQLException nicht mehr im Port), neuen Port `Transaktionsverwalter` (`<T> T inTransaktion(Arbeit<T>)`) und Port `Bestandszaehler` (falls nicht schon in 2.1) definieren. Verifikation: `mvn -pl raumschiffwerft-service compile` nach Abschluss aller Moves
- [x] 4.2 JDBC-Implementierungen nach `boundary/persistence` verschieben: `JdbcAuftragRepository`, `JdbcZustellungRepository`, `JdbcOutboxRepository`, `JdbcJournalOutboxRepository`, `JdbcBestandszaehler`, `AgroalTransaktionsverwalter` (Connection, setAutoCommit(false), commit/rollback) inkl. paketprivatem Verbindungshalter (ThreadLocal) fuer die gemeinsame Transaktionsverbindung; `AufraeumRepository` ebenfalls nach `boundary/persistence`. Verifikation: Unit-Tests der Control-Klassen mocken nur Ports, `AnnahmeControllerTest` ohne Agroal-Abhaengigkeit
- [x] 4.3 `AnnahmeController` auf Ports umstellen: Transaktion ueber `Transaktionsverwalter`, Repos ohne Connection-Parameter, `DatenbankNichtErreichbar`-Semantik (503-Weg) unveraendert. Verifikation: `AnnahmeIT` (400/200/202/503-Semantik) und `AnnahmeDatenbankWegIT` gruen
- [x] 4.4 `JournalRelay` entschlacken: Outbox-Zugriff (lesen, markieren) ueber Port `JournalOutboxRepository`, JDBC-Teil in `boundary/persistence`; Relay-Logik, Spans und Span-Links bleiben im control. Verifikation: `JournalRelayTest` gruen, `JournalRelayIT` gruen
- [x] 4.5 Boundary-Pakete umstrukturieren: `boundary/rest` (`KaufauftraegeResource`, `UngueltigeAnfrage`), `boundary/persistence` (alle JDBC-Klassen), `boundary/integration` (`ZustellRoute`, `CamelZustellport`, `AbgleichRoute`, `CamelAbgleichsport`, `AufraeumRoute`) mit `integration/journal` (`CamelJournalVersand`, `JournalRelayRoute`, `JournalConsumerRoute`, `JournalIndexVerwaltung`, `OpenSearchIndexClient`, `OpenSearchJournalIndex`) und `integration/aufraeumen`. Verifikation: keine Klassen mehr direkt in `boundary` (ausser package-info), `mvn -pl raumschiffwerft-service test-compile` gruen
- [x] 4.6 Alle Aufrufstellen und Tests auf neue Pakete umstellen (Import- und ArchUnit-konform). Verifikation: `mvn -pl raumschiffwerft-service -am test` gruen

## 5. Architekturtests

- [x] 5.1 `BoundaryControlEntityArchitectureTest` und weitere Regeln erweitern: control ohne Referenzen auf `..boundary..` (besteht), `io.agroal..`, `org.apache.camel..`, `java.sql..`/`javax.sql..`, `io.micrometer..` und `org.jboss.logging..`; Agroal/JDBC nur in `..boundary.persistence..`; Camel nur in `..boundary.integration..`. Verifikation: Regeln scheitern nachweislich bei einem provozierten Verstoß (kurz einbauen, sehen, entfernen) und laufen im Build gruen
- [x] 5.2 `ComponentArchitectureTest` auf neue Paketstruktur pruefen (Muster `..service..journal..` muss control/journal und boundary/integration/journal weiterhin korrekt trennen). Verifikation: `mvn -pl raumschiffwerft-service test -Dtest=*ArchitectureTest` gruen

## 6. Abschlusspruefung

- [x] 6.1 Gesamtbau: `mvn verify` in der devenv-Shell ueber alle vier Module mit laufender devenv-Infrastruktur (Postgres, Kafka, OpenSearch, OTel-Collector, WireMock); alle Unit-Tests und ITs gruen, Architekturtests laufen mit
- [x] 6.2 Observability-Endpruefung: Collector-Debug-Export zeigt zu einem Annahme-Request Trace, Metriken (Counter-Gaeltigkeit aus IT-Verhalten) und Logzeilen mit Trace-ID. Verifikation: devenv-Prozesslog des Collectors nach IT-Lauf sichten
- [x] 6.3 `openspec validate correct-o11y` ohne --strict ausfuehren und Fehler beheben
