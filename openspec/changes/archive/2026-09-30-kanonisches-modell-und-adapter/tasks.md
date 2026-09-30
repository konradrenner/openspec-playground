# Tasks: kanonisches-modell-und-adapter

## 1. Kanonisches Modell (raumschiffwerft-model)

- [x] 1.1 Wertobjekte und Enums im Package `model.entity` anlegen: `AuftragsId` (record um UUID), `Sternenzerstoererklasse`, `Zielsystemtyp`, `Zustellbestaetigung` (record, externe Referenz), `Verarbeitungsstatus` (Enum mit optionaler externer Referenz) — verifiziert durch Unit-Tests für Gleichheit von `AuftragsId` bei gleicher UUID und alle Enum-Werte
- [x] 1.2 `Kaufauftrag` (record) mit Validierung im kompakten Konstruktor (Käufer 1–100, Anzahl 1–12, Lieferplanet 1–60, nicht-null) anlegen — verifiziert durch Unit-Tests, die je Wertebereich gültige Werte annehmen und ungültige (leer/101 Zeichen, 0/13, 0/61) mit `IllegalArgumentException` ablehnen
- [x] 1.3 Checked Exception `ZustellungUngeklaert` und Port-Interface `Zielsystem` (`typ()`, `zustellen(AuftragsId, Kaufauftrag)`, `statusAbfragen(AuftragsId)`) im Package `model.boundary` anlegen — verifiziert durch `mvn -pl raumschiffwerft-model install` und Kompilierbarkeit einer Test-Implementierung
- [x] 1.4 Sicherstellen, dass das Modell-POM keine Framework-Abhängigkeiten hat — verifiziert per Blick in `raumschiffwerft-model/pom.xml` (nur JUnit/Mockito testweise) und erfolgreichem `mvn -pl raumschiffwerft-model verify`

## 2. Adapter Imperium (raumschiffwerft-adapter-imperium)

- [x] 2.1 WSDL `src/main/resources/wsdl/imperium-werft.wsdl` erstellen: SOAP 1.1 document/literal, Namespace `urn:org:kore:imperium:werft:v1`, Operationen `BestelleSternenzerstoerer` (auftragsReferenz, besteller, klasse ISD_I/ISD_II/VICTORY/EXECUTOR, stueckzahl, zielwelt → bestellnummer, eingangsstatus) und `AbfrageBestellstatus` (auftragsReferenz → status, bestellnummer) — verifiziert durch erfolgreiches Codegen
- [x] 2.2 `cxf-codegen-plugin` im Adapter-POM konfigurieren (generierte Klassen unter `target/generated-sources/cxf`) — verifiziert durch `mvn -pl raumschiffwerft-adapter-imperium generate-sources` mit generierten Java-Klassen
- [x] 2.3 `ImperiumZielsystem` (boundary, implementiert `Zielsystem`, `typ()` = IMPERIUM) mit injiziertem `@CXFClient("imperium")` und Übersetzungslogik im control-Package (Klassen-Mapping IMPERIAL_I→ISD_I, IMPERIAL_II→ISD_II, VICTORY→VICTORY, EXECUTOR→EXECUTOR; Status-Mapping IN_BEARBEITUNG/ABGESCHLOSSEN/UNBEKANNT; bestellnummer als externe Referenz) anlegen — verifiziert durch Unit-Tests mit gemocktem CXF-Client (Mockito, kein `@QuarkusTest`)
- [x] 2.4 Fehlerbehandlung zentral in `ImperiumZielsystem`: SOAP-Fault, HTTP-Fehler und Timeout werden als `ZustellungUngeklaert` gemeldet, keine Retries, kein DB-Zugriff — verifiziert durch Unit-Tests, die Fault/Fehlerfälle in `ZustellungUngeklaert` übersetzt sehen
- [x] 2.5 Client-Konfiguration in `application.properties` (Endpunkt-URL, 2-s-Timeouts) anlegen — verifiziert durch Startkontext im Integrationstest (2.7)

## 3. Adapter Rebellion (raumschiffwerft-adapter-rebellion)

- [x] 3.1 MicroProfile-REST-Client-Interface `RebellionBeschaffungClient` (`@RegisterRestClient(configKey="rebellion")`) mit `POST /api/v1/beschaffungen` und `GET /api/v1/beschaffungen/{referenz}/status` plus Request/Response-Records im entity-Package anlegen — verifiziert durch Kompilierbarkeit und Unit-Tests mit gemocktem Client
- [x] 3.2 `RebellionZielsystem` (boundary, implementiert `Zielsystem`, `typ()` = REBELLION) mit Übersetzungslogik im control-Package (Schiffstyp-Mapping imperial-1/imperial-2/victory/executor; Status-Mapping IN_ARBEIT→IN_BEARBEITUNG, ERLEDIGT→ABGESCHLOSSEN, UNBEKANNT→UNBEKANNT; beschaffungsId als externe Referenz) anlegen — verifiziert durch Unit-Tests mit gemocktem Client (Mockito)
- [x] 3.3 404 der Statusabfrage als Verarbeitungsstatus UNBEKANNT interpretieren; alle anderen Nicht-2xx-Antworten und Timeouts als `ZustellungUngeklaert` melden; keine Retries, kein DB-Zugriff — verifiziert durch Unit-Tests für 404, 500 und Timeout-Fall
- [x] 3.4 Client-Konfiguration in `application.properties` (Basis-URL, 2-s-Timeouts) anlegen — verifiziert durch den Integrationstest (3.6)

## 4. WireMock-Stubs (wiremock/mappings/)

- [x] 4.1 Imperium-Stubs anlegen: SOAP-Erfolg (bestellnummer ISD-4711), SOAP-Fault bei Käufer mit „Jar Jar", 5-s-Verzögerung bei „Langsam" — verifiziert durch `devenv up -d` und manuellen Abgleich der WireMock-Antworten (Admin-UI oder Request-Log)
- [x] 4.2 Imperium-Statusabfrage-Szenario (Scenario `imperium-status`: erst IN_BEARBEITUNG, dann ABGESCHLOSSEN) anlegen — verifiziert durch zwei aufeinanderfolgende Abfragen gegen den Stub
- [x] 4.3 Rebellion-Stubs anlegen: POST-Erfolg (beschaffungsId RB-1138), Fehlerstatus bei Käufer mit „Jar Jar", 5-s-Verzögerung bei „Langsam", GET-Status-Szenario (Scenario `rebellion-status`: erst IN_ARBEIT, dann ERLEDIGT) — verifiziert durch Abfragen gegen den Stub bzw. den Integrationstest (3.6)

## 5. Integrationstests und Gesamtbau

- [x] 5.1 `ImperiumAdapterIT` (`@QuarkusTest`, Failsafe): Erfolg liefert Zustellbestaetigung „ISD-4711", „Jar Jar" wirft `ZustellungUngeklaert`, „Langsam" bricht nach 2 s mit `ZustellungUngeklaert` ab, Statusabfrage-Szenario liefert erst IN_BEARBEITUNG dann ABGESCHLOSSEN — verifiziert durch `mvn -pl raumschiffwerft-adapter-imperium verify` mit laufendem WireMock
- [x] 5.2 `RebellionAdapterIT` (`@QuarkusTest`, Failsafe): Erfolg liefert Zustellbestaetigung „RB-1138", „Jar Jar" wirft `ZustellungUngeklaert`, „Langsam" bricht nach 2 s mit `ZustellungUngeklaert` ab, Status-Szenario liefert erst IN_BEARBEITUNG dann ABGESCHLOSSEN, unbekannte Referenz (404) liefert UNBEKANNT — verifiziert durch `mvn -pl raumschiffwerft-adapter-rebellion verify` mit laufendem WireMock
- [x] 5.3 Gesamtbau und Architektur: `mvn verify` im Wurzelverzeichnis gegen laufende devenv-Dienste — verifiziert durch grünen Build inkl. ArchUnit-Regeln (Layering, BCE, Camel-Freiheit) und Surefire/Failsafe ohne Fehler
