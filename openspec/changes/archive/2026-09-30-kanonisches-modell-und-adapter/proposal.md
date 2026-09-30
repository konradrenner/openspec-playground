# Proposal: kanonisches-modell-und-adapter

## Why

Der Werft-Service muss Aufträge an zwei fachlich verschiedene Zielsysteme (Imperium via SOAP, Rebellion via REST) melden; bislang existiert weder das kanonische interne Modell noch eine Adapter-Implementierung: Die Module enthalten nur Package-Gerüste. Dieser Change etabliert das gemeinsame Modell und die beiden Adapter als Grundlage für das spätere Routing im Service.

## What Changes

- **Neues kanonisches Modell** (`raumschiffwerft-model`): `AuftragsId` (UUID), `Sternenzerstoererklasse` (IMPERIAL_I, IMPERIAL_II, VICTORY, EXECUTOR), `Kaufauftrag` (Käufer 1–100 Zeichen, Klasse, Anzahl 1–12, Lieferplanet 1–60), `Zielsystemtyp` (IMPERIUM, REBELLION), `Zustellbestaetigung` (externe Referenz), `Verarbeitungsstatus` (ABGESCHLOSSEN, IN_BEARBEITUNG, UNBEKANNT, optional externe Referenz), checked Exception `ZustellungUngeklaert` sowie das Port-Interface `Zielsystem` mit `typ()`, `zustellen()`, `statusAbfragen()`.
- **Fehlervertrag für Adapter**: Jede `Zielsystem`-Implementierung meldet jeden Misserfolg (Nicht-2xx, SOAP-Fault, Timeout) als `ZustellungUngeklaert`, führt KEINE Retries aus und greift NICHT auf die Datenbank zu.
- **Neuer Adapter Imperium** (`raumschiffwerft-adapter-imperium`): SOAP 1.1 document/literal, Namespace `urn:org:kore:imperium:werft:v1`, Operationen `BestelleSternenzerstoerer` (→ Bestellnummer, Eingangsstatus) und `AbfrageBestellstatus`. WSDL im Adapter, Codegen mit `cxf-codegen-plugin`, Client per `@CXFClient("imperium")`, Client-Timeout 2 s.
- **Neuer Adapter Rebellion** (`raumschiffwerft-adapter-rebellion`): MicroProfile REST Client auf `POST /api/v1/beschaffungen` und `GET /api/v1/beschaffungen/{referenz}/status` (404 = UNBEKANNT), Client-Timeout 2 s.
- **WireMock-Stubs für beide Zielsysteme** (Erfolg ISD-4711 bzw. RB-1138, Fehler bei Käufer „Jar Jar", 5 s Verzögerung bei Käufer „Langsam", Statusabfrage-Szenario erst IN_BEARBEITUNG/IN_ARBEIT, dann ABGESCHLOSSEN/ERLEDIGT) als Testgrundlage.

Keine Breaking Changes: Alle Module sind bisher Gerüste; der Service selbst (REST-Schnittstelle, Camel-Routing, Persistenz) ist nicht Gegenstand dieses Changes.

## Capabilities

### New Capabilities

- `kanonisches-modell`: Kanonisches Datenmodell der Werft (DTOs, Wertobjekte, checked Exception `ZustellungUngeklaert`) und das Port-Interface `Zielsystem` mit einheitlichem Fehlervertrag.
- `adapter-imperium`: SOAP-Adapter für das Imperium (WSDL, Codegen, CXF-Client, Statusmapping, Fehlerbehandlung) inklusive WireMock-Teststubs.
- `adapter-rebellion`: REST-Adapter für die Rebellion (MicroProfile REST Client, Statusmapping, 404-Behandlung) inklusive WireMock-Teststubs.

### Modified Capabilities

Keine — die bestehenden Specs (`build-structure`, `module-architecture`) bleiben unverändert; der Change hält die dort etablierten Regeln ein.

## Impact

- **Code**: `raumschiffwerft-model` (entity: DTOs/Werte, boundary: Port `Zielsystem`), `raumschiffwerft-adapter-imperium` (WSDL + generierte Klassen, boundary/control), `raumschiffwerft-adapter-rebellion` (boundary/control).
- **Abhängigkeiten**: `quarkus-cxf` (Imperium, bereits deklariert), `quarkus-rest-client`/`quarkus-rest-client-jackson` (Rebellion, bereits deklariert), `cxf-codegen-plugin` neu im Adapter-POM.
- **Testinfrastruktur**: Neue Mapping-Dateien unter `wiremock/mappings/` (eine JSON-Datei pro Stub); Integrationstests (`*IT`, Failsafe) gegen den lokalen WireMock (Port 8089).
- **Nicht betroffen**: `raumschiffwerft-service` (Routing/REST folgt in einem späteren Change), Datenbankschema.
