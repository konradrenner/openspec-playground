# Observability Specification (Delta)

## ADDED Requirements

### Requirement: Anwendungs-Logging per java.util.logging
Der Anwendungscode MUSS ausschließlich die Java-Logging-API (`java.util.logging`) verwenden; der JBoss-Logger DARF im Anwendungscode NICHT verwendet werden. Logeinträge MÜSSEN über die OpenTelemetry-Log-Bridge den aktiven Trace-Kontext tragen und per OTLP exportiert werden.

#### Scenario: Kein JBoss-Logger im Anwendungscode
- **WHEN** der Anwendungscode des service nach Logger-Verwendungen durchsucht wird
- **THEN** findet sich ausschließlich java.util.logging als Logging-API

#### Scenario: Logzeilen tragen den Trace-Kontext und werden exportiert
- **WHEN** im Kontext eines Auftrags per java.util.logging geloggt wird
- **THEN** trägt die Logzeile die Trace-ID des aktiven Spans und erscheint in der Log-Pipeline des Collectors

## MODIFIED Requirements

### Requirement: Telemetrie-Export ueber OTLP
Alle drei Signale MUESSEN per OpenTelemetry ueber OTLP an den Collector gesendet werden: Traces (OTLP gRPC, Endpunkt konfigurierbar, Default localhost:4317), Metriken (OTLP, Default localhost:4318) und Logs (OTLP). Metriken und Logs MUESSEN in der Quarkus-Konfiguration explizit aktiviert werden, da sie in der verwendeten Quarkus-Version standardmäßig deaktiviert sind. Logzeilen MUESSEN die Trace-ID des aktiven Kontexts tragen; Traces MUESSEN im Collector-Log (Debug-Export der lokalen Umgebung) sichtbar sein.

#### Scenario: Annahme erzeugt einen Trace im Collector
- **WHEN** ein Auftrag angenommen wird
- **THEN** erscheint der Trace der Annahme (inklusive Zustell- und Journal-Anteilen) im Collector-Log

#### Scenario: Logzeilen tragen den Trace-Kontext
- **WHEN** im Kontext eines Auftrags geloggt wird
- **THEN** enthaelt die Logzeile die Trace-ID des aktiven Spans

#### Scenario: Metriken erscheinen im Collector
- **WHEN** ein Auftrag angenommen und Zustellungen verbucht werden
- **THEN** erscheinen die Betriebs-Metriken in der Metrik-Pipeline des Collectors

#### Scenario: Logs erscheinen im Collector
- **WHEN** die Anwendung Logzeilen im Kontext eines Auftrags ausgibt
- **THEN** erscheinen die Logeinträge in der Log-Pipeline des Collectors

### Requirement: Betriebs-Metriken
Es MUSS ein Counter `durchlauferhifter.zustellungen` mit den Tags zielsystem und ergebnis fuer jedes verbuchte Zustell- bzw. Abgleichsergebnis existieren. Es MUSS ein Counter `durchlauferhifter.lease.abgelaufen` mit dem Tag ausgangsstatus fuer jede nach abgelaufener Lease uebernommene (nach Absturz offene) Zustellung existieren. Es MUESSEN Gauges `durchlauferhifter.zustellungen.offen`, `durchlauferhifter.zustellungen.fehlgeschlagen` und `durchlauferhifter.outbox.rueckstand` (Anzahl ungesendeter Outbox-Zeilen) existieren. Alle Instrumente MUESSEN ausschließlich über die OpenTelemetry API erzeugt werden; Micrometer DARF im Anwendungscode und im Classpath des service NICHT verwendet werden.

#### Scenario: Zustell-Ergebnis zaehlt den Counter
- **WHEN** eine Zustellung als BESTAETIGT, UNGEKLAERT oder FEHLGESCHLAGEN verbucht wird
- **THEN** steigt der Counter durchlauferhifter.zustellungen mit zielsystem und ergebnis der Zustellung

#### Scenario: Absturz-Uebernahme zaehlt den Lease-Counter
- **WHEN** der Abgleich eine Zustellung mit abgelaufener Lease uebernimmt (Ausgangsstatus IN_ZUSTELLUNG oder IN_ABGLEICH)
- **THEN** steigt der Counter durchlauferhifter.lease.abgelaufen mit dem Ausgangsstatus als Tag

#### Scenario: Gauges zeigen den Betrieb
- **WHEN** die Metriken abgefragt werden
- **THEN** liefern die Gauges die aktuelle Zahl offener und fehlgeschlagener Zustellungen sowie den ungesendeten Outbox-Rueckstand

#### Scenario: Kein Micrometer
- **WHEN** der service gebaut wird
- **THEN** enthält der Abhängigkeitsbaum keine Micrometer-Artefakte und der Anwendungscode referenziert ausschließlich die OpenTelemetry API für Metriken
