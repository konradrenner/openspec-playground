# Build-Structure Specification (Delta)

## MODIFIED Requirements

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
