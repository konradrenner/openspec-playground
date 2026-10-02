## Why

Das Tracing ist der Backbone der Nachvollziehbarkeit, aktuell liefert aber nur das Quarkus-Plugin rudimentäre Spans: der HTTP-Request-Span der Annahme sowie die manuell gesetzten Spans `journal.relay`, `journal.indexieren` und `abgleich`. Was dazwischen passiert — jeder Camel-Routenschritt, jeder Datenbankzugriff, jeder Aufruf eines externen Systems (Imperium SOAP, Rebellion REST, Kafka, OpenSearch) — ist im Trace unsichtbar. Ein Auftrag laesst sich so nicht Ende-zu-Ende nachvollziehen.

## What Changes

- Camel-Routen erzeugen Spans: jeder Verarbeitungsschritt einer Route (Route selbst, Processoren, `choice`-Zweige, Kafka-Consumer/Producer) erscheint als eigener Span im Trace — Integration von `camel-quarkus-opentelemetry` in den `raumschiffwerft-service`.
- Datenbankzugriffe erscheinen im Trace: jede JDBC-Operation der Repositories (lesen, beanspruchen, verbuchen, Outbox) oeffnet einen eigenen Span mit Ziel-Attribut.
- Externe Aufrufe sind klar als solche erkennbar: Aufrufe der Adapter (Imperium via SOAP, Rebellion via REST) werden als Client-Spans mit Zielsystem-Attribut getrennt von der Verarbeitungslogik sichtbar; Kafka-Produktion/-Konsum und die OpenSearch-Indexierung laufen als eigene Spans.
- Span-Attribute `durchlauferhitzer.*` (auftrag.id, zielsystem, zustellstatus) bleiben Bestandteil der Zustell-, Abgleich- und Journal-Spans.
- Keine fachlichen Aenderungen: REST-Vertrag, Persistenz, Zustell- und Abgleichlogik bleiben unberuehrt — der Change ist rein beobachtbarkeitsseitig.

## Capabilities

### New Capabilities

<!-- keine -->

### Modified Capabilities

- `observability`: Die Spec um drei Anforderungen erweitert — Spans je Camel-Routenschritt, Spans je Datenbankzugriff, erkennbare Spans fuer Aufrufe externer Systeme. Die bestehenden Anforderungen (OTLP-Export, Trace-Kontext in auftrag/Journal, Relay-Verknuepfung, Span-Attribute, Betriebs-Metriken) bleiben unveraendert.

## Impact

- **Code**: `raumschiffwerft-service` (Camel-Routen `boundary/integration`, JDBC-Repositories `boundary/persistence`, Zustellungs-/Abgleichssteuerung `control`), ggf. `raumschiffwerft-adapter-imperium` und `raumschiffwerft-adapter-rebellion` fuer Client-Spans am Adapter-Boundary.
- **Abhaengigkeiten**: `camel-quarkus-opentelemetry` im service; OpenTelemetry-API ggf. in den Adaptern (Quarkus-APIs sind dort bereits erlaubt, Camel bleibt tabu).
- **Tests**: ObservabilityIT erweitert (Camel-/DB-Spans nachweisbar), neue/eingepasste Unit-Tests fuer Span-Helfer; ArchUnit-Regeln duerfen nicht gelockert werden.
- **Betrieb**: Mehr Spans pro Trace im OTel-Collector-Log und in Zipkin; Export-Verhalten (OTLP-Endpunkte, Signale) unveraendert.
