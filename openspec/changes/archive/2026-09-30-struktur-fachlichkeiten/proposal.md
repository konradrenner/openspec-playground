## Why

Die lokale Infrastruktur steht (Change `setup-grundgeruest`: devenv mit GraalVM, Postgres, Kafka, OpenSearch, OTel, WireMock), es existieren aber keine Java-Projekte. Die Raumschiffwerft soll Aufträge zum Bau von Sternenzerstörern (für Rebellion oder Imperium) über eine REST-Schnittstelle am Service entgegennehmen und via Apache Camel in das kanonische Datenmodell übersetzen und an die konkreten Ziele routen. Bevor die fachlichen Implementierungen als eigene Specs definiert werden, muss das Modulgerüst mit den richtigen Abhängigkeiten stehen.

## What Changes

- Maven-Parent-POM mit vier Modulen, groupId `org.kore.raumschiffwerft`; die Module sind intern nach Boundary-Control-Entity (BCE) paketiert:
  - `raumschiffwerft-model`: kanonisches internes Datenmodell (ausschließlich DTOs und falls notwendig Interfaces), keine Projektabhängigkeiten
  - `raumschiffwerft-adapter-imperium`: übersetzt vom kanonischen Modell ins Aufrufmodell des Imperiums (SOAP-Zielanbindung), hängt nur von model ab, Camel-frei
  - `raumschiffwerft-adapter-rebellion`: übersetzt vom kanonischen Modell ins Aufrufmodell der Rebellion (REST-Zielanbindung), hängt nur von model ab, Camel-frei
  - `raumschiffwerft-service`: REST-Schnittstelle (Aufträge anlegen/anzeigen), konvertiert eingehende Daten ins kanonische Modell und routet/transformiert via Camel, aggregiert Adapter und Modell als Quarkus-Anwendung
- Modulabhängigkeiten (zyklenfrei): `service -> adapter-imperium/adapter-rebellion -> model`
- Modularer Monolith: Die Adapter sind reine Bibliotheken (kein Anwendungs-Build, nicht standalone lauffähig); nur der service ist die lauffähige Quarkus-Anwendung
- Framework-Abhängigkeiten deklarieren (ohne Implementierung): Camel NUR im service (`camel-quarkus-core` neben `quarkus-rest` und `quarkus-rest-jackson`); adapter-imperium ruft SOAP über Jakarta-EE-APIs auf (`quarkus-cxf` als JAX-WS-Implementierung); adapter-rebellion ruft REST über den Jakarta-REST-Client auf (`quarkus-rest-client`, `quarkus-rest-client-jackson`); Versionen ausschließlich über BOM-Importe im Parent (quarkus-bom, quarkus-camel-bom, quarkus-cxf-bom, Test-BOMs)
- Build-Regeln: Surefire im Parent (Unit-Tests `*Test`, ohne `@QuarkusTest`), Failsafe im service und in den Adapter-Modulen (Integrationstests `*IT`, `@QuarkusTest` erlaubt — Adapter sind damit unabhängig testbar), Jandex-Index in den Adapter-Modulen, `native`-Profil im service (GraalVM)
- Erste ArchUnit-Regeln: Modul-Layering (model <- adapter <- service), interne BCE-Regeln, "kein `@QuarkusTest` in `*Test`" und "keine Camel-Klassen in Adaptern"
- Keine Fachlogik: DTOs, REST-Ressourcen, Camel-Routen und Zielsysteme werden in separaten Changes mit eigenen Specs definiert

## Capabilities

### New Capabilities
- `build-structure`: Maven-Multi-Module-Build mit Parent-POM, den vier Modulen und ihren modulspezifischen Framework-Abhängigkeiten (Quarkus REST und Jakarta-EE-Client-APIs, Camel nur im service), Adapter als Bibliotheken mit QuarkusTest-Bootstrap für Integrationstests (ohne Anwendungsartefakt), Test-Ausführung über Surefire (Unit) und Failsafe (Integration, in service und Adaptern), Jandex in den Adapter-Modulen, native Kompilierung möglich.
- `module-architecture`: Durch ArchUnit erzwungenes Modul-Layering (model <- adapter-imperium/rebellion <- service), interne BCE-Struktur je Modul, Camel-Freiheit der Adapter und die Konvention, dass Unit-Tests kein `@QuarkusTest` verwenden.

### Modified Capabilities

(none)

## Impact

- Neue POMs und Java-Paketskelette unter den vier Maven-Modulen; kein bestehender Code betroffen (die Module wurden zuvor gelöscht).
- `devenv.nix` bleibt unverändert (Infrastruktur aus `setup-grundgeruest`).
- Abhängigkeiten: Quarkus-Plattform-BOM (3.33.x LTS; u. a. quarkus-rest, quarkus-rest-client, quarkus-junit), quarkus-camel-bom (camel-quarkus-core), quarkus-cxf-bom (quarkus-cxf), JUnit 5, Mockito, ArchUnit, Surefire/Failsafe, SmallRye Jandex.
