## Purpose

Stellt die lokale Entwicklungsumgebung bereit: JDK 25, Maven und die Infrastruktur-Dienste Kafka, Postgres, OpenSearch, OTel Collector und WireMock — lokal ohne Docker, isoliert und reproduzierbar über devenv.nix.

## ADDED Requirements

### Requirement: devenv stellt GraalVM (Java 25) und Maven bereit
Die Entwicklungsumgebung MUSS via devenv.nix ein JDK 25 und Maven bereitstellen, sodass Build und Tests ohne vorab manuell installierte Werkzeuge in der devenv-Shell lauffähig sind. Das JDK MUSS GraalVM CE (Java 25 LTS) sein und `native-image` enthalten, damit native Kompilierung möglich ist.

#### Scenario: Werkzeuge in der devenv-Shell
- **WHEN** ein Entwickler die devenv-Shell betritt und `java --version` bzw. `mvn --version` ausführt
- **THEN** meldet `java` GraalVM CE auf Java-25-Basis (inkl. `native-image`) und `mvn` eine aktuelle Maven-Version

### Requirement: Infrastruktur-Dienste laufen via devenv
devenv.nix MUSS die Dienste Kafka, Postgres und OpenSearch starten können. Für Postgres MÜSSEN die Datenbank `durchlauferhitzer` und der User `durchlauferhitzer` eingerichtet sein. Die Dienste DÜRFEN nicht via Docker bereitgestellt werden.

#### Scenario: Dienste starten
- **WHEN** `devenv up` ausgeführt wird
- **THEN** sind Kafka, Postgres (mit Datenbank und User `durchlauferhitzer`) und OpenSearch erreichbar

#### Scenario: Postgres-Zugriff
- **WHEN** sich ein Client als User `durchlauferhitzer` mit der Datenbank `durchlauferhitzer` verbindet
- **THEN** wird die Verbindung angenommen

### Requirement: OTel Collector nimmt OTLP entgegen
devenv.nix MUSS einen OpenTelemetry-Collector starten, der OTLP-Signale auf den Ports 4317 (gRPC) und 4318 (HTTP) entgegennimmt und sie über den Debug-Exporter ausgibt.

#### Scenario: OTLP-Ausgabe im Debug-Exporter
- **WHEN** Telemetrie-Daten an Port 4317 oder 4318 gesendet werden
- **THEN** gibt der Collector die Daten lesbar im Debug-Exporter aus

### Requirement: WireMock auf Port 8089 mit JSON-Stubs
devenv.nix MUSS WireMock auf Port 8089 starten. Die Stub-Definitionen MÜSSEN als einzelne JSON-Dateien unter `wiremock/mappings` liegen und von der Laufzeitumgebung aus diesem Verzeichnis eingelesen werden.

#### Scenario: Stub wird ausgeliefert
- **WHEN** eine in `wiremock/mappings` als JSON-Datei definierte Anfrage an WireMock auf Port 8089 gesendet wird
- **THEN** antwortet WireMock mit der im Stub definierten Antwort

#### Scenario: Neuer Stub ohne Neustart nutzbar
- **WHEN** eine weitere JSON-Datei in `wiremock/mappings` abgelegt wird
- **THEN** wird der neue Stub von WireMock ohne Änderung an devenv.nix bedient

### Requirement: Anwendung startet gegen die lokalen Dienste
Die (in diesem Change leere) Quarkus-Anwendung MUSS mit `devenv up` laufenden Diensten starten können, ohne dass Dienste fehlen oder die Anwendung mit Konfigurationsfehlern abbricht.

#### Scenario: Start der Anwendung
- **WHEN** die Dienste via `devenv up` laufen und die Quarkus-Anwendung im dev-Modus gestartet wird
- **THEN** startet die Anwendung fehlerfrei
