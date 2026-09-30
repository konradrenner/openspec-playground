# Observability Specification

## Purpose

Verschreibt den Vertrag der Beobachtbarkeit der Raumschiffwerft: Telemetrie-Export ueber OTLP, Trace-Kontext in Auftraegen und Journaleintraegen, Verknuepfung und Propagierung des Kontexts vom Relay bis zur Indexierung, Span-Attribute sowie Betriebs-Metriken.

## Requirements


### Requirement: Telemetrie-Export ueber OTLP
Traces MUESSEN per OpenTelemetry ueber OTLP an den Collector gesendet werden (OTLP gRPC, Endpunkt konfigurierbar, Default localhost:4317). Metriken MUESSEN per OpenTelemetry-kompatiblem Registry ueber OTLP HTTP (Default localhost:4318) gesendet werden. Logzeilen MUESSEN die Trace-ID des aktiven Kontexts tragen; Traces MUESSEN im Collector-Log (Debug-Export der lokalen Umgebung) sichtbar sein.

#### Scenario: Annahme erzeugt einen Trace im Collector
- **WHEN** ein Auftrag angenommen wird
- **THEN** erscheint der Trace der Annahme (inklusive Zustell- und Journal-Anteilen) im Collector-Log

#### Scenario: Logzeilen tragen den Trace-Kontext
- **WHEN** im Kontext eines Auftrags geloggt wird
- **THEN** enthaelt die Logzeile die Trace-ID des aktiven Spans

### Requirement: Trace-Kontext in auftrag und Journaleintrag
Die Annahme MUSS die Trace-ID des aktiven Spans in der auftrag-Zeile (trace_id) und im Journaleintrag (traceId, traceparent) speichern. Ist kein eingehender `traceparent`-Header vorhanden, MUSS die Annahme einen eigenen Span starten — trace_id ist damit immer gesetzt. Ein vorhandener Client-Trace MUSS als Eltern-Kontext uebernommen werden.

#### Scenario: Annahme mit Client-Trace
- **WHEN** der POST mit gueltigem traceparent-Header ankommt
- **THEN** ist die gespeicherte trace_id die des uebernommenen Client-Traces

#### Scenario: Annahme ohne Client-Trace
- **WHEN** der POST ohne traceparent-Header ankommt
- **THEN** startet die Annahme einen eigenen Span und speichert dessen Trace-ID in auftrag und Journaleintrag

### Requirement: Relay verknuepft und propagiert den Kontext bis zur Indexierung
Der Journal-Relay MUSS pro versendetem Journaleintrag einen Span oeffnen und diesen per Span-Link mit dem im Journaleintrag gespeicherten traceparent verknuepfen. Sein Kontext MUSS ueber Kafka-Header (traceparent) an die Indexierung propagiert werden: Der Indexier-Span MUSS aus dem Kafka-Header-Kontext hervorgehen.

#### Scenario: Relay-Span ist mit dem Annahme-Trace verlinkt
- **WHEN** der Relay einen Journaleintrag versendet
- **THEN** traegt der Relay-Span einen Span-Link auf den gespeicherten traceparent des Annahme-Spans

#### Scenario: Indexier-Span ist Kind des Relay-Spans
- **WHEN** die Indexierung einen Journaleintrag verarbeitet
- **THEN** laeuft sie im Kontext des Relay-Spans aus dem Kafka-Header (traceparent)

### Requirement: Span-Attribute der Zustell- und Abgleich-Spans
Zustell- und Abgleich-Spans MUESSEN die Attribute `durchlauferhitzer.auftrag.id`, `durchlauferhifter.zielsystem` und — soweit im Moment des Ereignisses bekannt — `durchlauferhifter.zustellstatus` tragen.

#### Scenario: Zustell-Span traegt Auftrag und Zielsystem
- **WHEN** eine Zustellung durchgefuehrt wird
- **THEN** traegt ihr Span die Attribute durchlauferhifter.auftrag.id und durchlauferhifter.zielsystem

#### Scenario: Abgleich-Span traegt den Zustellstatus
- **WHEN** der Abgleich eine Zustellung verarbeitet
- **THEN** traegt der Span den Ausgangszustellstatus als durchlauferhifter.zustellstatus

### Requirement: Betriebs-Metriken
Es MUSS ein Counter `durchlauferhifter.zustellungen` mit den Tags zielsystem und ergebnis fuer jedes verbuchte Zustell- bzw. Abgleichsergebnis existieren. Es MUSS ein Counter `durchlauferhifter.lease.abgelaufen` mit dem Tag ausgangsstatus fuer jede nach abgelaufener Lease uebernommene (nach Absturz offene) Zustellung existieren. Es MUESSEN Gauges `durchlauferhifter.zustellungen.offen`, `durchlauferhifter.zustellungen.fehlgeschlagen` und `durchlauferhifter.outbox.rueckstand` (Anzahl ungesendeter Outbox-Zeilen) existieren.

#### Scenario: Zustell-Ergebnis zaehlt den Counter
- **WHEN** eine Zustellung als BESTAETIGT, UNGEKLAERT oder FEHLGESCHLAGEN verbucht wird
- **THEN** steigt der Counter durchlauferhifter.zustellungen mit zielsystem und ergebnis der Zustellung

#### Scenario: Absturz-Uebernahme zaehlt den Lease-Counter
- **WHEN** der Abgleich eine Zustellung mit abgelaufener Lease uebernimmt (Ausgangsstatus IN_ZUSTELLUNG oder IN_ABGLEICH)
- **THEN** steigt der Counter durchlauferhifter.lease.abgelaufen mit dem Ausgangsstatus als Tag

#### Scenario: Gauges zeigen den Betrieb
- **WHEN** die Metriken abgefragt werden
- **THEN** liefern die Gauges die aktuelle Zahl offener und fehlgeschlagener Zustellungen sowie den ungesendeten Outbox-Rueckstand
