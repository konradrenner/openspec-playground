## Purpose

Definiert den Maven-Build: Parent-POM mit vier Modulen, Java 25, BOM-Importe sowie die Aufteilung von Unit-Tests (Surefire) und Integrationstests (Failsafe) inklusive Jandex-Index für die Adapter.

## ADDED Requirements

### Requirement: Parent-POM mit vier Modulen
Es MUSS ein Maven-Parent-POM mit der groupId `org.kore.durchlauferhitzer` existieren, das die vier Module `durchlauferhitzer-model`, `durchlauferhitzer-adapter-rest`, `durchlauferhitzer-adapter-soap` und `durchlauferhitzer-service` baut. Die Module MÜSSEN intern nach Boundary-Control-Entity (boundary, control, entity) paketiert sein und DÜRFEN in diesem Change keine Fachlogik enthalten.

#### Scenario: Build des Eltern-POMs
- **WHEN** `mvn verify` auf dem Parent-POM ausgeführt wird
- **THEN** werden alle vier Module erfolgreich gebaut

### Requirement: Build gegen Java 25 mit BOM-Importe
Der Build MUSS Java 25 als Release-Ziel verwenden; als JDK MUSS GraalVM CE (Java 25 LTS, inkl. `native-image`) bereitgestellt sein, sodass native Kompilierung möglich ist. Abhängigkeitsversionen MÜSSEN über BOM-Importe (Quarkus-BOM und Test-BOMs) im Parent-POM verwaltet werden; einzelne Module geben KEINE eigenen Versionen an.

#### Scenario: Kompilierung mit Java 25
- **WHEN** der Build ausgeführt wird
- **THEN** kompiliert der Quellcode gegen Java 25 (Release 25)

#### Scenario: Versionen kommen aus BOMs
- **WHEN** ein Modul eine Abhängigkeit deklariert
- **THEN** stammt deren Version aus einem im Parent-POM importierten BOM

### Requirement: Unit-Tests laufen via Surefire im Parent
Das Surefire-Plugin MUSS im Parent-POM konfiguriert sein und die Unit-Tests aller Module ausführen. Die Tests MÜSSEN als `*Test` benannt sein.

#### Scenario: Unit-Test-Ausführung
- **WHEN** `mvn test` auf dem Parent-POM ausgeführt wird
- **THEN** führt Surefire die Unit-Tests aller Module aus

### Requirement: Integrationstests laufen via Failsafe im Modul service
Das Failsafe-Plugin MUSS im Modul `durchlauferhitzer-service` konfiguriert sein und Integrationstests (Namenskonvention `*IT`) ausführen.

#### Scenario: Integrationstest-Ausführung
- **WHEN** `mvn verify` auf dem Parent-POM ausgeführt wird
- **THEN** führt Failsafe die Integrationstests im Modul `durchlauferhitzer-service` aus

### Requirement: Jandex-Index in den Adapter-Modulen
In den Modulen `durchlauferhitzer-adapter-rest` und `durchlauferhitzer-adapter-soap` MUSS beim Build ein Jandex-Index erzeugt werden, damit Quarkus die Adapter-Klassen ohne weitere Hilfsmittel finden kann.

#### Scenario: Index-Datei nach dem Build
- **WHEN** die Module `durchlauferhitzer-adapter-rest` und `durchlauferhitzer-adapter-soap` gebaut werden
- **THEN** enthalten die gebauten Artefakte jeweils einen Jandex-Index (`META-INF/jandex.idx`)
