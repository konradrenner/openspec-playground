## Why

Das Projekt hat bislang nur Basistooling (devenv mit Quarkus-CLI und openspec), aber keine lokale Entwicklungsumgebung. Für jede fachliche Änderung fehlt das Fundament: eine lokal laufende Infrastruktur mit den benötigten Diensten, gegen die später gebaut, getestet und gestartet werden kann.

## What Changes

- devenv.nix: GraalVM CE 25 als JDK (inkl. native-image), Maven, Kafka, Postgres (DB und User `raumschiffwerft`), OpenSearch, OTel-Collector (OTLP auf 4317/4318, Debug-Exporter) und WireMock auf Port 8089 als devenv-Services; WireMock-Stubs als einzelne JSON-Dateien unter `wiremock/mappings`.

## Capabilities

### New Capabilities
- `local-dev-environment`: Lokale Entwicklungsumgebung via devenv.nix mit GraalVM CE 25 (Java 25, native-image), Maven, Kafka, Postgres (DB und User `raumschiffwerft`), OpenSearch, OTel-Collector (OTLP 4317/4318, Debug-Exporter) und WireMock auf 8089 mit JSON-Stubs aus `wiremock/mappings`.

### Modified Capabilities

(none)

## Impact

- `devenv.nix` wird erweitert (JDK, Maven, Services, WireMock-Stub-Verzeichnis).
- Neue Verzeichnisse: `otelcol/`, `wiremock/mappings`.

> Hinweis (Revision, nutzerinitiiert): Der Java-Grundgerüst-Teil dieses Changes (Module,
> Build, ArchUnit) wurde nach der Loeschung des generierten Skeletts in den Change
> `struktur-fachlichkeiten` ueberfuehrt und wird dort Schritt fuer Schritt mit der
> Fachlichkeit Raumschiffwerft neu geplant.
