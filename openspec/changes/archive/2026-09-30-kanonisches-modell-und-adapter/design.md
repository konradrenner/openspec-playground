# Design: kanonisches-modell-und-adapter

## Context

Alle vier Maven-Module existieren als BCE-Gerüste (nur `package-info.java`), die Parent-POM deklariert bereits `quarkus-cxf` (Imperium-Adapter) und `quarkus-rest-client`/`quarkus-rest-client-jackson` (Rebellion-Adapter) inkl. `quarkus-cxf-bom`. Der lokale WireMock läuft auf Port 8089 mit Stubs aus `wiremock/mappings/` (eine JSON-Datei pro Stub). ArchUnit-Tests im Service erzwingen bereits Modul-Layering, BCE und Camel-Freiheit der Adapter. Motivation und Verhaltenskontrakte: siehe proposal.md und specs/.

## Goals / Non-Goals

**Goals:**

- Kanonisches Modell in `raumschiffwerft-model` als reine Java-Typen (record/enum), framework-frei.
- Port `Zielsystem` als Hexagonal-Port im Modell; Adapter implementieren ihn.
- Zwei Adapter (Imperium via CXF/SOAP, Rebellion via MicroProfile REST Client) mit einheitlicher Fehlerbehandlung (`ZustellungUngeklaert`, keine Retries, kein DB-Zugriff) und 2-s-Client-Timeout.
- WireMock-Stubs plus Integrationstests (`*IT`, Failsafe) für beide Adapter.

**Non-Goals:**

- Kein REST-Endpoint, kein Camel-Routing, keine Persistenz im Service (spätere Changes).
- Keine Wiederholungs-/Kompensationslogik; der Aufrufer entscheidet über den Umgang mit `ZustellungUngeklaert`.
- Keine Anpassung der bestehenden ArchUnit-Regeln.

## Decisions

### D1: Modell als Java-Records und Enums, Validierung im Konstruktor

- `AuftragsId` (record um `UUID`), `Kaufauftrag` (record), `Zustellbestaetigung` (record), `Verarbeitungsstatus` als Enum mit optionaler externer Referenz (statische Fabrikmethoden statt nullbarer Felder), `Sternenzerstoererklasse` und `Zielsystemtyp` als Enums.
- Korrektur aus der Umsetzung: `Zielsystem.zustellen` nimmt `AuftragsId` und `Kaufauftrag` entgegen — die AuftragsId ist die `auftragsReferenz` bzw. `referenz` der Zielsysteme; ohne sie könnte `statusAbfragen(AuftragsId)` den Auftrag beim Ziel nicht wiederfinden, und ein Adapter-internes Merken wäre Zustand (unerwünscht, kein DB-Zugriff).
- `Kaufauftrag` validiert im kompakten Konstruktor (Käufer 1–100, Anzahl 1–12, Lieferplanet 1–60) und wirft `IllegalArgumentException` mit benanntem Feld. Records liefern equals/hashCode gratis (Anforderung „AuftragsId gleich bei gleicher UUID").
- Alternative: Jakarta-Bean-Validation. Abgelehnt: Zusatzabhängigkeit und Framework-Kopplung im Modell, das laut Kontext framework-frei bleiben muss.
- Platzierung: Wertobjekte und DTOs in `org.kore.raumschiffwerft.model.entity`; Port `Zielsystem` in `org.kore.raumschiffwerft.model.boundary` (Boundary = Schnittstelle nach außen; das Modell bleibt BCE-konform, da `boundary` auf `entity` zugreifen darf).

### D2: `ZustellungUngeklaert` als checked Exception im Modell

- Im Modell (boundary-Package), damit der Port sie in der Signatur führt und kein Adapter eine eigene Exception-Hierarchie braucht.
- Trägt die Ursache (`cause`) der technischen Ausnahme für Diagnose, ohne deren Typ nach außen zu leaken.

### D3: Imperium-Adapter: WSDL-first mit cxf-codegen-plugin und @CXFClient

- WSDL (`imperium-werft.wsdl`) unter `src/main/resources/wsdl/` des Adapter-Moduls; SOAP 1.1, document/literal, Namespace `urn:org:kore:imperium:werft:v1`, Operationen `BestelleSternenzerstoerer` und `AbfrageBestellstatus`.
- Codegen per `cxf-codegen-plugin` (aus der `quarkus-cxf-bom` verwalteten Version) in `target/generated-sources/cxf`; generierte Klassen bleiben unangetastet.
- Client als CDI-Bean: Quarkus-CXF injiziert den Service-Port per `@CXFClient("imperium")`; Endpunkt-URL konfigurierbar via `application.properties` (`quarkus.cxf.client."imperium".client-endpoint-url`), im Test auf den WireMock (Port 8089).
- Timeout: `quarkus.cxf.client."imperium".connection-timeout`/`receive-timeout` = 2000 ms.
- Struktur: generierte Service-Typen liegen als Fremdkörper außerhalb der BCE-Packages (`org.kore.raumschiffwerft.adapter.imperium.werft.v1`); `control` hält die Übersetzungslogik (Klassen-Mapping IMPERIAL_I→ISD_I usw., Status-Mapping), `boundary` die `Zielsystem`-Implementierung (`ImperiumZielsystem`), die Exceptions fängt und in `ZustellungUngeklaert` übersetzt.
- Alternative: JAX-WS `wsimport` oder Handbau der JAXB-Typen. Abgelehnt: Duplikat des bestehenden Quarkus-CXF-Setups, fehleranfällig.

### D4: Rebellion-Adapter: MicroProfile REST Client

- Interface `RebellionBeschaffungClient` mit `@RegisterRestClient(configKey="rebellion")`, `@POST @Path("/api/v1/beschaffungen")` und `@GET @Path("/api/v1/beschaffungen/{referenz}/status")`; DTOs (Request/Response) als Records im `entity`-Package mit Jackson-Annotationen nur, wo nötig.
- Timeout: `quarkus.rest-client."rebellion".connect-timeout`/`read-timeout` = 2000 ms; Basis-URL auf den WireMock im Test.
- `boundary`/`control` analog D3: `RebellionZielsystem` implementiert `Zielsystem`, `control` hält Schiffstyp- (imperial-1/imperial-2/victory/executor) und Status-Mapping (IN_ARBEIT→IN_BEARBEITUNG, ERLEDIGT→ABGESCHLOSSEN).
- 404 bei der Statusabfrage wird abgefangen und als UNBEKANNT interpretiert (nur dort; 404 beim POST bleibt ein Misserfolg → `ZustellungUngeklaert`).

### D5: Fehlerbehandlung — zentral in der Boundary, keine Retries

- Beide Adapter fangen sämtliche technischen Fehler (SOAP-Fault, `WebApplicationException`, Nicht-2xx, `ProcessingException`/Timeout) an genau einer Stelle — der `Zielsystem`-Implementierung — ab und werfen `ZustellungUngeklaert(cause)`.
- Kein Retry-Mechanismus, kein DB-Zugriff in den Adaptern (Kontrakt aus der Spec; Camel-Freiheit sichert ArchUnit, DB wäre ohnehin nicht erreichbar, da die Adapter nur vom Modell abhängen).

### D6: WireMock-Stubs als Mapping-Dateien plus Szenario-Technik

- Pro Stub eine JSON-Datei unter `wiremock/mappings/` (Konvention aus devenv/AGENTS.md).
- Käufer-„Jar Jar" und „Langsam" via Request-Matching auf JSON-Path im Body (`$.auftraggeber` bzw. `$.besteller`); Verzögerung über `fixedDelayMilliseconds: 5000`.
- Statusabfrage-Szenarios über WireMock-Scenarios (State `started` → erste Antwort IN_BEARBEITUNG/IN_ARBEIT, State-Übergang nach `finished` → ABGESCHLOSSEN/ERLEDIGT). Jeweils getrennte Stubs pro Adapter (unterschiedliche Pfade/Nachrichtenformate), getrennte Szenario-Namen (`imperium-status`, `rebellion-status`).

### D7: Teststrategie

- Unit-Tests (`*Test`, Surefire, ohne `@QuarkusTest`): Mapping-Logik mit Reinform-Daten und gemockten Client-Ergebnissen (Mockito).
- Integrationstests (`*IT`, Failsafe, `@QuarkusTest` erlaubt): Adapter gegen den lokalen WireMock — Erfolg, „Jar Jar"-Fehler, „Langsam"-Timeout, Status-Szenario. Test-Property-Dateien zeigen die Client-URLs auf `http://localhost:8089`.
- Kein zusätzlicher ArchUnit-Test im Model: Die Framework-Freiheit des Modells ergibt sich aus der leeren Dependency-Liste des Modell-POMs; ein Architekturtest für nicht existierende Referenzen wäre Totholz.

## Risks / Trade-offs

- [CXF-Codegen-Output widerspricht BCE-Package-Struktur] → Generierte Klassen bewusst im eigenen Wurzel-Package (nicht boundary/control/entity); ArchUnit-BCE-Regeln prüfen nur die drei bekannten Packages, generierter Code fällt heraus. Dokumentation im package-info des Adapters.
- [WireMock-Stub-Matching auf Käufer ist fragil gegenüber Feldnamen] → Body-Matching mit exakten Feldnamen der jeweiligen API (besteller/auftraggeber) und unabhängigen Tests pro Stub; beim POST entscheidet der Stub-URL-Pfad zusätzlich.
- [2-s-Timeout vs. 5-s-Verzögerungs-Stub: Testlaufzeit] → Nur der „Langsam"-Test braucht den Timeout-Abbruch (nach 2 s), nie die volle Verzögerung; Test-Suite bleibt schnell.
- [Quarkus-CXF-Propertiesyntax mit Anführungszeichen in Keys] → Keys im Test verifizieren; Start des Adapter-IT deckt Tippfehler sofort auf.
- [404-Behandlung nur für Statusabfrage definiert] → Im Design festgehalten; falls der Service später 404 beim POST braucht, ist das ein neuer Change.

## Migration Plan

Reines Additivum in bisher leeren Modulen; keine bestehende Nutzung betroffen. Rollback = Rückbau der neuen Klassen, Stub-Dateien und POM-Einträge; keine Datenmigration nötig.

## Open Questions

Keine — offene Punkte (Feldnamen der Stub-APIs, Property-Keys) werden in den Integrationstests verifiziert.
