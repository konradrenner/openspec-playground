# Raumschiffwerft

Spielwiese für [OpenSpec](https://github.com/Fission-AI/OpenSpec) und Apache Camel: Die Raumschiffwerft nimmt Kaufaufträge für Sternenzerstörer entgegen, stellt sie an das Imperium (SOAP) oder die Rebellion (REST) zu, journaleinträge werden über Kafka nach OpenSearch indexiert.

## Umgebung

Alles läuft über devenv (kein Docker):

```bash
direnv allow                # oder: devenv shell
devenv up -d                # Postgres, Kafka, OpenSearch, OTel-Collector, WireMock starten
devenv processes status postgres
```

Service im Dev-Modus starten:

```bash
mvn -pl raumschiffwerft-service quarkus:dev
```

Ports: Service 8080, WireMock 8089, Postgres 5432, OpenSearch 9200, OTel-Collector 4317 (gRPC) / 4318 (HTTP).

## Service-API

Annahme (Idempotency-Key ist Pflicht und wird zur Auftrags-ID; `traceparent` optional — ohne startet der Service einen eigenen Trace):

```bash
curl -sS -X POST http://localhost:8080/api/v1/kaufauftraege \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d' \
  -H 'traceparent: 00-0af7651916cd43dd8448eb4c9cbbfda1-0af7651916cd43dd-01' \
  -d '{"kaeufer": "Tarkin", "klasse": "IMPERIAL_I", "anzahl": 2, "lieferplanet": 42}'
```

Abfrage:

```bash
curl -sS http://localhost:8080/api/v1/kaufauftraege/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
```

- 200: alle Zustellungen bestätigt · 202: noch offen (`Location`-Header) · 400: ungültiger Body/Header · 404: unbekannt · 503: Datenbank weg.
- Liefernplanet 4 („Yavin 4"), 5 („Hoth"), 6 („Dantooine") gehen an die Rebellion, alles andere ans Imperium (Feature-Flag `flags/flags.json`).

## WireMock-Fälle nachfahren

Die Stubs liegen unter `wiremock/mappings/` und matchen auf den Besteller (`Tarkin` = Erfolg, `Jar Jar Binks` = Fehler 500, `Langsam Kaufmann` = 5 s Verzögerung, Client-Timeout 2 s).

Imperium (SOAP, `POST http://localhost:8089/imperium`):

```bash
# Bestellung: Erfolg (Bestellnummer ISD-4711)
curl -sS -X POST http://localhost:8089/imperium \
  -H 'Content-Type: text/xml' \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body><bestelleSternenzerstoerer xmlns="urn:org:kore:imperium:werft:v1"><besteller>Tarkin</besteller></bestelleSternenzerstoerer></soapenv:Body></soapenv:Envelope>'

# Bestellung: Fehler (Jar Jar — Bonitätsprüfung schlägt fehl, SOAP-Fault 500)
curl -sS -X POST http://localhost:8089/imperium \
  -H 'Content-Type: text/xml' \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body><bestelleSternenzerstoerer xmlns="urn:org:kore:imperium:werft:v1"><besteller>Jar Jar Binks</besteller></bestelleSternenzerstoerer></soapenv:Body></soapenv:Envelope>'

# Statusabfrage: Szenario — erster Aufruf IN_BEARBEITUNG, danach ABGESCHLOSSEN
curl -sS -X POST http://localhost:8089/imperium \
  -H 'Content-Type: text/xml' \
  -d '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body><abfrageBestellstatus xmlns="urn:org:kore:imperium:werft:v1"><auftragsReferenz>00000000-0000-0000-0000-000000000042</auftragsReferenz></abfrageBestellstatus></soapenv:Body></soapenv:Envelope>'
```

Rebellion (REST):

```bash
# Beschaffung anlegen: Erfolg (201, RB-1138)
curl -sS -X POST http://localhost:8089/api/v1/beschaffungen \
  -H 'Content-Type: application/json' \
  -d '{"auftraggeber": "Mon Mothma", "schiffstyp": "VICTORY", "anzahl": 1}'

# Beschaffung anlegen: Fehler (500, Jar Jar)
curl -sS -X POST http://localhost:8089/api/v1/beschaffungen \
  -H 'Content-Type: application/json' \
  -d '{"auftraggeber": "Jar Jar Binks", "schiffstyp": "VICTORY", "anzahl": 1}'

# Statusabfrage: 404 = UNBEKANNT (nur für diese feste Beschaffungs-ID)
curl -sS http://localhost:8089/api/v1/beschaffungen/00000000-0000-0000-0000-000000000404/status

# Statusabfrage: Szenario — erster Aufruf IN_ARBEIT, danach ERLEDIGT (jede andere ID)
curl -sS http://localhost:8089/api/v1/beschaffungen/00000000-0000-0000-0000-000000000042/status
```

WireMock-Szenarien (Status-Sequenz IN_BEARBEITUNG → ABGESCHLOSSEN bzw. IN_ARBEIT → ERLEDIGT) vor jedem Nachfahren zurücksetzen:

```bash
curl -sS -X POST http://localhost:8089/__admin/scenarios/reset
```

Health und Ping:

```bash
curl -sS http://localhost:8089/__admin/health   # WireMock selbst
curl -sS http://localhost:8089/ping             # Ping-Stub
```

## Observability

- **Traces** (`quarkus-opentelemetry`) gehen per OTLP gRPC an den Collector (4317), **Metriken** (`micrometer-registry-otlp`) per OTLP HTTP (4318): Counter `durchlauferhitzer.zustellungen` (zielsystem, ergebnis), `durchlauferhifter.lease.abgelaufen` (ausgangsstatus), Gauges `durchlauferhifter.zustellungen.offen`, `.fehlgeschlagen`, `durchlauferhifter.outbox.rueckstand`.
- **Wo die Traces erscheinen**: Der OTel-Collector exportiert im Debug-Modus in sein Prozesslog:

  ```bash
  devenv processes logs opentelemetry-collector
  ```

  Nach einer Annahme erscheinen dort der Request-Span (`POST /api/v1/kaufauftraege/...`) mit den Zustell-Anteilen, der Relay-Span `journal.relay` (per Span-Link mit dem Annahme-Trace verknüpft) und `journal.indexieren`. Suche nach der Trace-ID aus dem `traceparent`-Header (oder `auftrag.trace_id` in der Datenbank).

- **Logs** tragen die Trace-ID des aktiven Kontexts in der Konsole; ein vollständiger OTLP-Log-Export ist in dieser Quarkus-Version nicht verfügbar (Traces und Metriken gehen vollständig über OTLP).
- Das Journal ist in OpenSearch nachlesbar (Index `durchlauferhitzer-journal`, Dokument-ID = Auftrags-ID, `rohPayload` nicht indiziert):

  ```bash
  curl -sS http://localhost:9200/durchlauferhitzer-journal/_doc/<auftrags-id>
  ```

## Bauen und Tests

```bash
mvn verify        # Unit- (Surefire), Architektur- (ArchUnit) und Integrationstests (Failsafe)
```

## End-to-End-Simulation

Ein Aufruf deckt die ganze Kette ab (baut den Runner bei Bedarf, startet den Service mit kurzen Abgleich-/Journal-Intervallen und stoppt ihn wieder):

```bash
devenv up -d
e2e
```

Szenarien: erfolgreiche Imperium- und Rebellion-Zustellung, Idempotenz (versuche bleibt 1), Fehler- und Timeout-Zustellung mit Konvergenz über den Abgleich zu BESTAETIGT, Journaleintrag mit Trace-ID in OpenSearch sowie die REST-Fehlersemantik (400/404). Service-Log des Laufs: `/tmp/raumschiffwerft-e2e.log`.
