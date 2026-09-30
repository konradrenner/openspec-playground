## Context

Greenfield-Projekt: existierend sind nur devenv (Quarkus-CLI, openspec) und die OpenSpec-Planungsdateien (siehe proposal.md - Why). Die vier Modulnamen wurden mit dem Nutzer bestätigt: `durchlauferhitzer-domain`, `durchlauferhitzer-application`, `durchlauferhitzer-adapters`, `durchlauferhitzer-service`. Fachlogik ist explizit ausgeschlossen; es geht nur um das Gerüst, gegen das später fachliche Changes implementiert werden.

## Goals / Non-Goals

**Goals:**
- Ein `mvn verify` auf dem Parent baut alle vier Module grün, ohne Docker und ohne manuell installierte Werkzeuge.
- Die Infrastruktur-Dienste sind über `devenv up` startbar und die leere Quarkus-App startet dagegen.
- Architektur- und Testkonventionen sind ab sofort maschinell gesichert (ArchUnit, Surefire/Failsafe-Split).

**Non-Goals:**
- Keine Fachlogik, keine REST-Endpunkte, keine Persistenz-Implementierung.
- Keine produktionsnahe Infrastruktur (z. B. kein TLS, keine Authentifizierung der Dienste, kein produktiver OTel-Exporter).
- Keine vollständigen BCE-Klassenregeln (Package-Level-Regeln innerhalb der Module folgen mit der ersten Fachlogik).

## Decisions

### 1. Modulstruktur und Abhängigkeiten
Vier Module, intern nach Boundary-Control-Entity (BCE) paketiert: `durchlauferhitzer-model` (kanonisches Modell, keine Projektabhängigkeiten), `durchlauferhitzer-adapter-rest` und `durchlauferhitzer-adapter-soap` (je ein Adapter pro Kanal, hängen nur vom Modell ab) sowie `durchlauferhitzer-service` (Kernprojekt mit den Nachrichtenfluesse; aggregiert Adapter und Modell als Quarkus-Anwendung). Java-Packages je Modul: `org.kore.durchlauferhitzer.model`, `org.kore.durchlauferhitzer.adapter.rest.{boundary,control,entity}`, `org.kore.durchlauferhitzer.adapter.soap.{boundary,control,entity}` und `org.kore.durchlauferhitzer.service.{boundary,control,entity}` (Bootstrap-Klasse direkt in `org.kore.durchlauferhitzer.service`). Adapter mappen auf das kanonische Modell; die Kopplung ans Service-Innere erfolgt spaeter nur ueber dessen Boundary — dadurch bleibt der Graph zyklenfrei (service -> adapter -> model).
- Frühere Variante (domain/application/adapters/service) verworfen — nutzerinitiierte Revision: BCE als interne Struktur, ein Adapter-Modul pro Kanal (REST, SOAP), Modell als eigenes kanonisches Modul.
- Alternative verworfen: zusaetzliches Ports-/Boundary-Modul — die Boundary-Schnittstellen des Service leben in dessen boundary-Package; ein fuenftes Modul waere fuer das Grundgeruest Over-Engineering (KISS).

### 2. Versions- und Dependency-Management
Parent-POM importiert die Quarkus-BOM (Plattform-Version als Property), zusätzlich Test-BOMs. Module deklarieren Abhängigkeiten ohne Versionen. Ziel-Release: Java 25 (`maven.compiler.release=25`), bereitgestellt von devenv.
- Alternative verworfen: Versionen je Modul — verstößt gegen Single Source of Truth und den Parent-Gedanken.

### 3. Plugin-Platzierung
- Surefire im Parent mit `default-test`-Execution: Unit-Tests (`*Test`, Mockito/JUnit 5, ohne Quarkus) laufen in allen Modulen.
- Failsafe NUR im Modul `durchlauferhitzer-service`: Integrationstests (`*IT`, `@QuarkusTest` erlaubt) brauchen die kompakte Applikation; nur dort ist das Quarkus-Test-Framework sinnvoll angebunden.
- Jandex (Quarkus-Jandex-Plugin) im Modul `durchlauferhitzer-adapters`: Quarkus findet die Adapter-Klassen später per Index, ohne dass das Modul selbst von Quarkus abhängt.
- Alternative verworfen: Failsafe im Parent — würde in Modulen ohne Applikations-Bootstrap zu Fehlkonfigurationen führen.

### 4. ArchUnit-Regeln leben als Unit-Tests im Modul `durchlauferhitzer-service`
Nur dort liegt der komplette Klassenpfad aller Module, sodass Modul-Layering und moduluebergreifende Regeln prüfbar sind. Die Regeln laufen mit Surefire und damit in jedem `mvn verify`. Drei Regelwerke:
- Modul-Layering: `model` referenziert nichts, Adapter-Module referenzieren nur `model` (service prueft und aggregiert alles).
- Interne BCE-Regeln global ueber alle Module: `..entity..` abhaengig von nichts aus `..control..`/`..boundary..`, `..control..` nicht von `..boundary..`.
- `noClasses().that().haveNameMatching(".*Test").should().beAnnotatedWith("QuarkusTest")`.
- Alternative verworfen: separates `architecture`-Modul — ein zusaetzliches Modul nur fuer die Regeln ist Over-Engineering (KISS-Verstoß).

### 5. devenv.nix: Sprachen, Dienste und Prozesse
- GraalVM CE 25 (`languages.java.jdk.package`, Java-25-LTS-Basis inkl. `native-image`) via `languages.java`, Maven via `languages.java.maven.enable` — native Kompilierung ist damit möglich (Quarkus: `mvn -Pnative package`, Profil im Modul `durchlauferhitzer-service`).
- Dienste konsequent als devenv-Services statt manueller Prozesse: `services.postgres` (Datenbank und User `durchlauferhitzer` per `initialDatabases`, TCP via `listen_addresses = "127.0.0.1"`), `services.kafka`, `services.opensearch`, `services.opentelemetry-collector` (eigene Konfigurationsdatei `otelcol/config.yaml` mit OTLP-Receiver 4317/4318, Debug-Exporter und `health_check`-Extension für die Readiness-Prüfung) und `services.wiremock` (Port 8089, `rootDir` auf das Projektverzeichnis `wiremock`, sodass die JSON-Stubs aus `wiremock/mappings` gelesen werden — pro Stub eine Datei).
- Alternative verworfen: Docker-Compose für Dienste — widerspricht der Vorgabe "lokal ohne Docker". Manuelle `processes` für OTel/WireMock — verworfen, da devenv hierfür eigenständige Services mit Readiness-Prüfung und Port-Verwaltung bereitstellt (nutzerinitiierte Revision).

### 6. Leere Quarkus-App im Modul `durchlauferhitzer-service`
Minimaler Bootstrap: Application-Klasse plus `application.properties` mit den Verbindungsdaten zu Postgres/Kafka (Host/Port aus devenv-Defaults). Absichtlich keine Extensions mit Startzeit-Prüfung (z. B. kein Agroal-Datasource-Zwang) — die App startet auch, wenn einzelne Dienste aus sind; Verbindungsdaten werden erst mit der ersten Fachlogik wirklich genutzt. KISS: keine Spekulation über künftigen Bedarf.

## Risks / Trade-offs

- [devenv-Service-Optionen weichen von den Annahmen ab (Postgres-User-Anlage, OpenSearch-Support)] -> Bei der Umsetzung prüfen; falls kein `users`-Hook existiert, User/Datenbank per Postgres-Init-Skript (`services.postgres.initdb`) anlegen.
- [WireMock-Package in nixpkgs ist nicht in jeder Version vorhanden] -> Fallback: WireMock als Prozess mit dem offiziellen JAR aus einem nixpkgs-Derivat prüfen; notfalls Paket-Pin dokumentieren.
- [Java 25 / Quarkus-Kompatibilität: Quarkus unterstützt Java 25 ggf. erst ab bestimmter Plattform-Version] -> Plattform-Version als Property halten; Ziel-Release bleibt 25, Quarkus-Version ist die jüngste LTS, die Java 25 offiziell unterstützt.
- [ArchUnit-Regeln prüfen nur Modul-/Package-Ebene] -> In Kauf genommen; BCE-Feinregeln (z. B. Entity referenziert keine Boundary-Klasse) folgen mit der ersten Fachlogik.

## Migration Plan

Reines Greenfield-Setup — kein paralleler Bestand. Rollback ist `git revert` des Changes; devenv wird durch Neueintritt in die Shell neu evaluiert.

## Open Questions

- Keine — offen gebliebene Details (konkrete Quarkus-Plattform-Version, exakte devenv-Optionssyntax) sind reine Umsetzungsfragen und ändern weder Specs noch Aufgabenzerlegung.
