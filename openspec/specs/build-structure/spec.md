# Build-Structure Specification

## Purpose

Definiert den Maven-Build der vier Java-Projekte der Raumschiffwerft: Parent-POM, Modulabhängigkeiten, modulspezifische Framework-Abhängigkeiten (Quarkus REST, Apache Camel, SOAP- und REST-Zielanbindung) sowie die Test- und Index-Plugins.

## Requirements

### Requirement: Parent-POM mit vier Modulen
Es MUSS ein Maven-Parent-POM mit der groupId `org.kore.raumschiffwerft` existieren, das die vier Module `raumschiffwerft-model`, `raumschiffwerft-adapter-imperium`, `raumschiffwerft-adapter-rebellion` und `raumschiffwerft-service` baut. Die Module MÜSSEN intern nach Boundary-Control-Entity (boundary, control, entity) paketiert sein und DÜRFEN in diesem Change keine Fachlogik enthalten.

#### Scenario: Build des Eltern-POMs
- **WHEN** `mvn verify` auf dem Parent-POM ausgeführt wird
- **THEN** werden alle vier Module erfolgreich gebaut
### Requirement: Modulabhängigkeiten sind zyklenfrei und folgen dem Ziel-Layering
`raumschiffwerft-model` MUSS ohne Projektabhängigkeiten bleiben. Die Adapter-Module `raumschiffwerft-adapter-imperium` und `raumschiffwerft-adapter-rebellion` MÜSSEN ausschließlich von `raumschiffwerft-model` abhängen. `raumschiffwerft-service` MUSS beide Adapter-Module aggregieren. Kein Modul DARF in umgekehrter Richtung abhängen.

#### Scenario: Abhängigkeitsgraph im Build prüfbar
- **WHEN** `mvn dependency:tree` über alle Module ausgeführt wird
- **THEN** enthält der Baum des service beide Adapter-Module, die Bäume der Adapter ausschließlich das model, und der Baum des model keine Projektartefakte
### Requirement: Framework-Abhängigkeiten je Modul deklariert
Das Modul `raumschiffwerft-service` MUSS `quarkus-rest`, `quarkus-rest-jackson`, `camel-quarkus-core` und `camel-quarkus-bean-validator` deklarieren — Routing und Transformation via Camel ist allein Sache des service. Der service MUSS alle drei OpenTelemetry-Signale (Traces, Metriken, Logs) über `quarkus-opentelemetry` mit seinen eingebauten OTLP-Exportern abdecken und DARF KEINE Micrometer-Abhängigkeiten (`quarkus-micrometer`, Micrometer-Registries) deklarieren. Das Modul `raumschiffwerft-adapter-imperium` MUSS für den SOAP-Aufruf des Imperiums über Jakarta-EE-APIs `quarkus-cxf` (Jakarta XML Web Services) deklarieren. Das Modul `raumschiffwerft-adapter-rebellion` MUSS für den REST-Aufruf der Rebellion über den Jakarta-REST-Client `quarkus-rest-client` und `quarkus-rest-client-jackson` deklarieren. Für QuarkusTest-basierte Integrationstests MÜSSEN die Adapter-Module `quarkus-junit` als Test-Abhängigkeit deklarieren. Die Adapter-Module DÜRFEN KEINE Camel-Abhängigkeiten deklarieren. Das Modul `raumschiffwerft-model` MUSS ohne Framework-Abhängigkeiten bleiben und DARF als einzige Fremd-Abhängigkeit die Jakarta Bean Validation API (`jakarta.validation-api`) mit Scope `provided` deklarieren, damit sie nicht im kompilierten Artefakt des Moduls landet und nicht transitiv an Adapter oder Service weitergereicht wird. Alle Versionen MÜSSEN aus im Parent-POM importierten BOMs (quarkus-bom, quarkus-camel-bom, quarkus-cxf-bom, Test-BOMs) stammen; Module geben KEINE eigenen Versionen an.

#### Scenario: Abhängigkeiten sind versioniert über BOMs
- **WHEN** ein Modul eine Framework-Abhängigkeit deklariert
- **THEN** ist die Version durch einen BOM-Import des Parent-POMs verwaltet und nicht im Modul angegeben

#### Scenario: Kompilierung gegen Java 25
- **WHEN** der Build ausgeführt wird
- **THEN** kompiliert der Quellcode gegen Java 25 (Release 25)

#### Scenario: Bean-Validation-API des Modells ist provided
- **WHEN** `mvn dependency:tree` über alle Module ausgeführt wird
- **THEN** enthält der Baum des model die Jakarta Bean Validation API nur mit Scope provided, und die Bäume der Adapter enthalten sie nicht als transitive Abhängigkeit des Modells

#### Scenario: Kein Micrometer im service
- **WHEN** `mvn dependency:tree` für den service ausgeführt wird
- **THEN** enthält der Baum keine Micrometer-Artefakte
### Requirement: Adapter sind Bibliotheken im modularen Monolithen
Die Adapter-Module MÜSSEN als Bibliotheken gebaut werden: Sie DÜRFEN KEINE lauffähige Anwendung erzeugen und das `build`-Ziel des Quarkus-Maven-Plugins NICHT verwenden. Für QuarkusTest-basierte Integrationstests DÜRFEN die Adapter-Module das Quarkus-Maven-Plugin mit den Goals `generate-code` und `generate-code-tests` konfigurieren — das schreibt das App-Modell für die Tests, ohne ein Anwendungsartefakt zu erzeugen. Nur das Modul `raumschiffwerft-service` ist die lauffähige Quarkus-Anwendung.

#### Scenario: Adapter erzeugen gewöhnliche Bibliotheks-JARs
- **WHEN** `mvn verify` auf dem Parent-POM ausgeführt wird
- **THEN** erzeugen die Adapter-Module gewöhnliche Bibliotheks-JARs (ohne Anwendungs-Bootstrap), und nur der service erzeugt die Anwendungsartefakte

#### Scenario: QuarkusTest-Bootstrap im Adapter
- **WHEN** ein mit `@QuarkusTest` annotierter Integrationstest (`*IT`) in einem Adapter-Modul ausgeführt wird
- **THEN** bootet eine Quarkus-Test-App aus dem Adapter-Modul heraus (mit den Extensions des Adapters), ohne dass der Adapter eine Anwendung erzeugt
### Requirement: Unit-Tests laufen via Surefire im Parent
Das Surefire-Plugin MUSS im Parent-POM konfiguriert sein und die Unit-Tests aller Module ausführen. Die Tests MÜSSEN als `*Test` benannt sein und DÜRFEN `@QuarkusTest` nicht verwenden.

#### Scenario: Unit-Test-Ausführung
- **WHEN** `mvn test` auf dem Parent-POM ausgeführt wird
- **THEN** führt Surefire die Unit-Tests aller Module aus
### Requirement: Integrationstests laufen via Failsafe im service und in den Adaptern
Das Failsafe-Plugin MUSS im Modul `raumschiffwerft-service` und in den Adapter-Modulen `raumschiffwerft-adapter-imperium` und `raumschiffwerft-adapter-rebellion` konfiguriert sein und Integrationstests (Namenskonvention `*IT`) ausführen; dort ist `@QuarkusTest` erlaubt. Damit sind die Adapter unabhängig vom service testbar.

#### Scenario: Integrationstest-Ausführung
- **WHEN** `mvn verify` auf dem Parent-POM ausgeführt wird
- **THEN** führt Failsafe die Integrationstests im Modul `raumschiffwerft-service` und in beiden Adapter-Modulen aus
### Requirement: Jandex-Index in den Adapter-Modulen
In den Modulen `raumschiffwerft-adapter-imperium` und `raumschiffwerft-adapter-rebellion` MUSS beim Build ein Jandex-Index erzeugt werden, damit Quarkus die Adapter-Klassen findet.

#### Scenario: Index-Datei nach dem Build
- **WHEN** die Adapter-Module gebaut werden
- **THEN** enthalten die gebauten Artefakte jeweils einen Jandex-Index (`META-INF/jandex.idx`)
### Requirement: Native Kompilierung ist möglich
Im Modul `raumschiffwerft-service` MUSS ein `native`-Profil existieren, das die native Kompilierung mit GraalVM aktiviert.

#### Scenario: Native-Profil vorhanden
- **WHEN** das Modul `raumschiffwerft-service` mit `mvn -Pnative help:evaluate -Dquarkus.native.enabled` geprüft wird
- **THEN** ist die Eigenschaft `quarkus.native.enabled` im Profil definiert
