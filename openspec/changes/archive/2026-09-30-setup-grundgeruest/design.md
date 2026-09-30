## Context

Greenfield-Projekt: existierend sind nur devenv (Quarkus-CLI, openspec) und die OpenSpec-Planungsdateien (siehe proposal.md - Why). Fachlogik und Java-Build sind explizit ausgeschlossen; dieser Change schafft ausschließlich die lokale Infrastruktur.

## Goals / Non-Goals

**Goals:**
- Die Infrastruktur-Dienste sind über `devenv up` startbar; Build und Start laufen ohne Docker und ohne manuell installierte Werkzeuge.
- GraalVM CE 25 als JDK inkl. `native-image`, damit native Kompilierung möglich ist.

**Non-Goals:**
- Kein Java-Build, keine Module, keine Anwendung (folgt mit dem Change `struktur-fachlichkeiten`).
- Keine produktionsnahe Infrastruktur (z. B. kein TLS, keine Authentifizierung der Dienste, kein produktiver OTel-Exporter).

## Decisions

### 1. devenv.nix: Sprachen, Dienste und Services
- GraalVM CE 25 (`languages.java.jdk.package`, Java-25-LTS-Basis inkl. `native-image`) via `languages.java`, Maven via `languages.java.maven.enable`.
- Dienste als devenv-Services: `services.postgres` (Datenbank und User `raumschiffwerft` per `initialDatabases`, TCP via `listen_addresses = "127.0.0.1"`), `services.kafka`, `services.opensearch`, `services.opentelemetry-collector` (eigene Konfigurationsdatei `otelcol/config.yaml` mit OTLP-Receiver 4317/4318, Debug-Exporter und `health_check`-Extension für die Readiness-Prüfung) und `services.wiremock` (Port 8089, `rootDir` auf das Projektverzeichnis `wiremock`, sodass die JSON-Stubs aus `wiremock/mappings` gelesen werden — pro Stub eine Datei).
- Alternative verworfen: Docker-Compose für Dienste — widerspricht der Vorgabe "lokal ohne Docker". Manuelle `processes` für OTel/WireMock — verworfen, da devenv hierfür eigenständige Services mit Readiness-Prüfung und Port-Verwaltung bereitstellt (nutzerinitiierte Revision).

## Risks / Trade-offs

- [devenv-Service-Optionen weichen von den Annahmen ab] -> Bei Abweichungen prüfen und per Init-Skript/Option nachziehen.
- [Java 25 / native-image: GraalVM-CE-Builds in nixpkgs können lang sein] -> GraalVM CE 25 war im nix-Store verfügbar; notfalls Build vorab anstoßen.

## Migration Plan

Reines Greenfield-Setup — kein paralleler Bestand. Rollback ist `git revert` des Changes; devenv wird durch Neueintritt in die Shell neu evaluiert.

## Open Questions

- Keine — die Java-Projektstruktur wird im Change `struktur-fachlichkeiten` geplant.
