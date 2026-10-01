# Module-Architecture Specification (Delta)

## ADDED Requirements

### Requirement: Control ist frei von Boundary und Infrastruktur
Klassen des control DÜRFEN KEINE Klassen aus boundary-Paketen und KEINE technischen Infrastruktur-Klassen (z. B. Agroal-Datenquellen, Camel, Micrometer, JDBC) referenzieren. Jede Abhängigkeit des control auf die Außenwelt MUSS als Port (Interface im control) ausgedrückt sein, dessen Implementierung in der boundary liegt (Dependency Inversion). Persistenz-Dienste (Repositories, Transaktionssteuerung) MÜSSEN als Ports im control deklariert und in `boundary/persistence` implementiert sein.

#### Scenario: Agroal-Referenz im control wird abgelehnt
- **WHEN** eine Klasse des control eine Klasse aus `io.agroal..` referenziert
- **THEN** scheitert der Architekturtest und nennt die betroffene Klasse

#### Scenario: Camel-Referenz im control wird abgelehnt
- **WHEN** eine Klasse des control eine Klasse aus `org.apache.camel..` referenziert
- **THEN** scheitert der Architekturtest und nennt die betroffene Klasse

#### Scenario: JDBC im control wird abgelehnt
- **WHEN** eine Klasse des control `java.sql..` oder `javax.sql..` referenziert
- **THEN** scheitert der Architekturtest und nennt die betroffene Klasse

#### Scenario: Ports statt Implementierungen
- **WHEN** das control Persistenz oder externe Systeme braucht
- **THEN** referenziert es ausschließlich eigene Port-Interfaces, deren Implementierungen in der boundary liegen

## MODIFIED Requirements

### Requirement: Module sind intern nach Boundary-Control-Entity strukturiert
Jedes Modul MUSS intern die Packages boundary, control und entity haben. Die BCE-Richtung MUSS eingehalten werden: `entity` referenziert KEINE control- oder boundary-Klassen; `control` referenziert KEINE boundary-Klassen; `boundary` kennt control und entity. Die boundary MUSS zudem in die technischen Subpakete `rest` (JAX-RS-Schnittstelle), `persistence` (JDBC, Datenquelle, Transaktion) und `integration` (Camel-Routen, Kafka, OpenSearch und sonstige externe Anbindungen) gegliedert sein; fachliche Gruppen DÜRFEN darunter als Unterpakete erhalten bleiben (z. B. `integration/journal`, `integration/aufraeumen`). Datenbankzugriff MUSS ausschließlich in `boundary/persistence` liegen, Camel-Route und externe Anbindungen ausschließlich in `boundary/integration`.

#### Scenario: BCE-Verstoß wird erkannt
- **WHEN** eine Klasse aus einem `entity`-Package eine Klasse aus einem `control`- oder `boundary`-Package referenziert, oder eine `control`-Klasse eine `boundary`-Klasse referenziert
- **THEN** scheitert der Architekturtest und nennt die betroffene Klasse

#### Scenario: Datenbankzugriff liegt nur in der boundary-Persistence
- **WHEN** eine Klasse außerhalb von `boundary/persistence` eine Datenquelle oder JDBC-Klassen referenziert
- **THEN** scheitert der Architekturtest und nennt die betroffene Klasse

#### Scenario: Camel liegt nur in der boundary-Integration
- **WHEN** eine Klasse außerhalb von `boundary/integration` eine Camel-Klasse referenziert
- **THEN** scheitert der Architekturtest und nennt die betroffene Klasse
