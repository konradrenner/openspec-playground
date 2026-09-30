## Why

Das Projekt hat bislang nur Basistooling (devenv mit Quarkus-CLI und openspec), aber weder ein Buildsystem noch eine lauffähige Anwendung. Für jede fachliche Änderung fehlt das Fundament: ein Maven-Multi-Module-Build, der die DDD/BCE-Struktur erzwingt, eine lokale Entwicklungsumgebung mit den benötigten Diensten und eine leere Quarkus-App, die gegen diese Dienste startet. Dies muss zuerst geschaffen werden.

## What Changes

- Maven-Parent-POM mit vier Modulen (noch ohne Fachlogik), groupId `org.kore.durchlauferhitzer`; die Module sind intern nach Boundary-Control-Entity (BCE) paketiert:
  - `durchlauferhitzer-model` (kanonisches Modell, keine Abhängigkeiten)
  - `durchlauferhitzer-adapter-rest` (REST-Adapter, hängt nur von model ab)
  - `durchlauferhitzer-adapter-soap` (SOAP-Adapter, hängt nur von model ab)
  - `durchlauferhitzer-service` (Kernprojekt/Nachrichtenfluesse, aggregiert Adapter und Modell, Quarkus-App)
- Build: Java 25 (GraalVM CE inkl. native-image), Maven, BOM-Importe (Quarkus, Test-BOMs), Surefire im Parent, Failsafe im Modul service, Jandex-Index in den Adapter-Modulen.
- devenv.nix: JDK (GraalVM CE 25), Maven, Kafka, Postgres (DB und User `durchlauferhitzer`), OpenSearch, OTel-Collector (OTLP auf 4317/4318, Debug-Exporter) und WireMock auf Port 8089 als devenv-Services; WireMock-Stubs als einzelne JSON-Dateien unter `wiremock/mappings`.
- Leere Quarkus-App, die gegen diese Dienste startet.
- Erste ArchUnit-Regeln: Modul-Layering (model <- adapter <- service), interne BCE-Regeln (entity kennt nichts nach außen, control kennt kein boundary) und "kein `@QuarkusTest` in `*Test`"-Klassen.

## Capabilities

### New Capabilities
- `local-dev-environment`: Lokale Entwicklungsumgebung via devenv.nix mit GraalVM CE 25 (Java 25, native-image), Maven, Kafka, Postgres (DB und User `durchlauferhitzer`), OpenSearch, OTel-Collector (OTLP 4317/4318, Debug-Exporter) und WireMock auf 8089 mit JSON-Stubs aus `wiremock/mappings`.
- `build-structure`: Maven-Multi-Module-Build mit Parent-POM und den vier Modulen, Test-Ausführung über Surefire (Unit) und Failsafe (Integration), Jandex in den Adapter-Modulen.
- `module-architecture`: Durch ArchUnit erzwungenes Modul-Layering (model <- adapter-rest/soap <- service), interne BCE-Struktur je Modul und die Konvention, dass Unit-Tests kein `@QuarkusTest` verwenden.

### Modified Capabilities

(none)

## Impact

- Neuer Java-Source unter den vier Maven-Modulen; kein bestehender Code betroffen (Greenfield).
- `devenv.nix` wird erweitert (JDK, Maven, Services, WireMock-Stub-Verzeichnis).
- Neue Verzeichnisse: Maven-Module inkl. Parent-POM, `wiremock/mappings`.
- Abhängigkeiten: Quarkus BOM, Quarkus Jandex, ArchUnit, JUnit 5, Mockito, Surefire/Failsafe.
