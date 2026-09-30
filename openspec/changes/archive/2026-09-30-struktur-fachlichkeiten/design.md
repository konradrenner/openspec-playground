## Context

Die devenv-Infrastruktur steht (siehe Change `setup-grundgeruest`); Java-Projekte wurden bewusst gelöscht und werden nun mit geklärter Fachlichkeit neu aufgesetzt: Die Raumschiffwerft nimmt Aufträge für Sternenzerstörer (Rebellion oder Imperium) entgegen. Der service konvertiert die eingehenden Daten seiner REST-Schnittstelle ins kanonische interne Modell und routet/transformiert via Camel an die konkreten Ziele. Die Adapter übersetzen vom kanonischen Modell in das Aufrufmodell des jeweiligen externen Dienstes (Imperium via SOAP, Rebellion via REST) und rufen ihn über Jakarta-EE-APIs auf — in den Adaptern ist Camel tabu. Ausgeliefert wird alles als modularer Monolith. Dieser Change umfasst nur Struktur und Abhängigkeiten; die fachlichen Implementierungen folgen als eigene Specs.

## Goals / Non-Goals

**Goals:**
- Vier Java-Projekte mit korrektem Abhängigkeitsgraph (service -> adapter -> model) und modulspezifischen Framework-Abhängigkeiten; `mvn verify` grün, service startet als Quarkus-Anwendung.
- Struktur maschinell gesichert: ArchUnit für Modul-Layering, interne BCE-Struktur und Test-Konventionen.

**Non-Goals:**
- Keine Fachlogik: keine DTOs mit Inhalten, keine REST-Ressourcen, keine Camel-Routen, keine WSDL/SOAP- oder REST-Client-Implementierung (eigene Changes mit eigenen Specs).
- Keine Erweiterung der devenv-Infrastruktur.
- Kein vollständiger Native-Build (nur die Möglichkeit dazu).

## Decisions

### 1. Namensgebung nach Ziel, nicht nach Protokoll; Adapter sind Camel-frei
Module: `raumschiffwerft-model`, `raumschiffwerft-adapter-imperium`, `raumschiffwerft-adapter-rebellion`, `raumschiffwerft-service`. Die Adapter sind nach ihrem Ziel (Faktion) benannt; das Protokoll ist Eigenschaft des Adapters: imperium = SOAP, rebellion = REST. Datenfluss: service konvertiert REST-Eingaben ins kanonische Modell; die Adapter übersetzen vom kanonischen Modell in das Aufrufmodell des externen Dienstes. Camel kennen die Adapter NICHT — Routing und Transformation via Camel ist allein Sache des service (maschinell per ArchUnit erzwungen).
- Alternative verworfen: Benennung nach Protokoll (`adapter-soap`, `adapter-rest`) — aus fachlicher Sicht ist das Ziel das Strukturierende, das Protokoll ein Implementierungsdetail; ein Ziel könnte theoretisch das Protokoll wechseln.

### 2. Kanonisches Modell als eigenständiges, abhängigkeitsfreies Modul
`raumschiffwerft-model` enthält ausschließlich DTOs und falls notwendig Interfaces und hat KEINE Abhängigkeiten — weder auf Projekte noch auf Frameworks. Der Service übersetzt in das Modell, die Adapter übersetzen daraus ins Aufrufmodell des externen Dienstes. Dadurch ist das Modell für beide Seiten stabil und frei von Technik-Details.
- Alternative verworfen: DTOs je Adapter im Adapter-Modul — würde das kanonische Modell (gemeinsame Sprache zwischen service und Adaptern) auflösen.

### 3. Modularer Monolith: Adapter sind Bibliotheken
`service -> adapter-imperium/adapter-rebellion -> model`, zyklenfrei. Die Adapter sind reine Bibliotheken: kein Quarkus-Anwendungs-Build (kein Quarkus-Maven-Plugin), keine `application.properties`, nicht standalone lauffähig. Nur der service ist die lauffähige Quarkus-Anwendung und aggregiert die Adapter.
- Konsequenz: Adapter-Module brauchen einen Jandex-Index, damit Quarkus ihre Klassen in der Anwendung findet, ohne dass der Adapter ein Anwendungsmodul ist.
- Alternative verworfen: je Adapter eine eigene lauffähige App (Microservice-artig) — Vorgabe ist der modulare Monolith.

### 4. Framework-Abhängigkeiten (deklariert, nicht implementiert)
- service: `quarkus-rest` + `quarkus-rest-jackson` (eigene REST-Schnittstelle für Aufträge), `camel-quarkus-core` (Routing/Transformation ins kanonische Modell und an die Ziele).
- adapter-imperium: `io.quarkiverse.cxf:quarkus-cxf` — SOAP-Aufruf des Imperiums über Jakarta-EE-APIs (Jakarta XML Web Services); Quarkus-API nur, wo notwendig.
- adapter-rebellion: `quarkus-rest-client` + `quarkus-rest-client-jackson` — REST-Aufruf der Rebellion über den Jakarta-REST-Client.
- Versionen ausschließlich via BOM-Importe im Parent-POM: `io.quarkus.platform:quarkus-bom` (Quarkus REST, REST-Client, Jackson, Plugins), `io.quarkus.platform:quarkus-camel-bom` (camel-quarkus-core) und `io.quarkus.platform:quarkus-cxf-bom` (quarkus-cxf) — Verfügbarkeit aller Artefakte in Version 3.33.3.3 verifiziert — sowie Test-BOMs (junit-bom, mockito-bom) und eine verwaltete ArchUnit-Version.
- Alternative verworfen: Camel-Komponenten in den Adaptern (camel-quarkus-cxf-soap/camel-quarkus-http) — Camel wäre in die Bibliotheken gelangt; verworfen, da die Adapter bewusst Camel-frei bleiben (nutzerinitiierte Revision).

### 5. Plugin-Platzierung
- Surefire im Parent (Unit-Tests `*Test`, Mockito/JUnit 5, ohne `@QuarkusTest`, in allen Modulen).
- Failsafe im Modul service UND in beiden Adapter-Modulen (`*IT`, `@QuarkusTest` erlaubt) — die Adapter sind damit unabhängig testbar (siehe Entscheidung 7).
- Jandex (SmallRye) in beiden Adapter-Modulen.
- Quarkus-Maven-Plugin im service mit `build`-Ziel und `native`-Profil; in den Adapter-Modulen ausschließlich mit den Goals `generate-code`/`generate-code-tests` (kein `build`-Ziel).
- ArchUnit-Tests als Unit-Tests im service-Modul, da nur dort der komplette Klassenpfad aller Module liegt (Regeln: Modul-Layering, interne BCE-Richtung, Camel-Freiheit der Adapter, kein `@QuarkusTest` in `*Test`).

### 6. Bootstrap ohne Fachlogik
Der service erhält eine minimale Application-Klasse und eine `application.properties` mit den devenv-Verbindungsdaten (aus `setup-grundgeruest`) — das ist Grundgerüst, damit der service als Quarkus-Anwendung startet. Keine REST-Ressource, keine Route.

### 7. QuarkusTest-basierte Integrationstests in den Adaptern (empirisch verifiziert)
Die Adapter-Module sollen unabhängig vom service integrierbar getestet werden können. Mechanismus (verifiziert mit Quarkus 3.33.3.3 / Java 25 / GraalVM CE): `@QuarkusTest` bootet auch in einem reinen Bibliotheksmodul eine Quarkus-Test-App, wenn das Quarkus-Maven-Plugin die Goals `generate-code` und `generate-code-tests` explizit in einer `execution` bindet — diese schreiben das App-Modell für die Tests. Wichtig:
- Die Goals MÜSSEN explizit gebunden werden; `<extensions>true</extensions>` allein bindet sie NICHT, der Test fällt dann auf die Laufzeit-Auflösung des App-Modells zurück (beobachtet: Class-Initialization-Deadlock im Bootstrap-Resolver).
- Ohne `build`-Ziel entsteht KEIN Anwendungsartefakt: Das Modul bleibt ein gewöhnliches Bibliotheks-JAR (verifiziert: kein `quarkus-app`-Verzeichnis), der modulare Monolith bleibt intakt.
- Die gebootete Test-App enthält nur die Extensions des Adapters (z. B. startet ein Adapter mit nur `quarkus-rest-client` keinen HTTP-Server) — isolierter Testkontext.
- Voraussetzungen je Adapter: `quarkus-junit` (Test-Abhängigkeit), Failsafe-Ausführung für `*IT` und mindestens eine Quarkus-Laufzeitextension (bei den Adaptern ohnehin vorhanden).
- Alternative verworfen: Integrationstests nur im service — Adapter wären nicht unabhängig testbar; Alternative verworfen: Adapter als eigene Anwendungen — verstößt gegen den modularen Monolithen.

## Risks / Trade-offs

- [quarkus-cxf könnte für die spätere fachliche SOAP-Umsetzung Einschränkungen haben (Native-Support, WSDL-Tooling)] -> Versionierung ist verifiziert (quarkus-cxf-bom 3.33.3.3); bei der fachlichen Implementierung prüfen und notfalls Alternative bewerten — betrifft dann einen fachlichen Change, nicht die Struktur.
- [ArchUnit-Regeln sehen nur Testklassen auf dem service-Klassenpfad] -> Die anderen Module sind faktisch geschützt, weil ihnen die Quarkus-Test-Abhängigkeit fehlt; in Kauf genommen (KISS).
- [Model ohne Interfaces-Definition jetzt] -> "falls notwendig Interfaces" — ob Interfaces nötig sind, entscheiden die fachlichen Specs; das Gerüst erzwingt nichts.

## Migration Plan

Greenfield — kein paralleler Bestand. Rollback ist `git revert` des Changes.

## Open Questions

- Keine für die Struktur — offene fachliche Details (DTO-Felder, Routen, WSDL, REST-Verträge) sind bewusst den nachfolgenden fachlichen Changes vorbehalten und ändern weder die Modulstruktur noch die Abhängigkeiten.
