# Adapter-Imperium Specification

## Purpose

Setzt das Port-Interface `Zielsystem` für das Imperium um: Übersetzung kanonischer Aufträge in SOAP-Aufrufe (Bestellung und Statusabfrage) gegen die Imperiumswerft, inklusive einheitlicher Fehlerbehandlung und WireMock-Teststubs für die Integrationstests.

## Requirements

### Requirement: SOAP-Vertrag der Imperiumswerft
Der Adapter MUSS SOAP 1.1 im document/literal-Stil mit dem Namespace `urn:org:kore:imperium:werft:v1` verwenden und die Operationen `BestelleSternenzerstoerer` (Parameter auftragsReferenz, besteller, klasse ISD_I/ISD_II/VICTORY/EXECUTOR, stueckzahl, zielwelt; Rückgabe bestellnummer, eingangsstatus) und `AbfrageBestellstatus` (Parameter auftragsReferenz; Rückgabe status IN_BEARBEITUNG/ABGESCHLOSSEN/UNBEKANNT und bestellnummer) anbieten bzw. aufrufen. Die WSDL MUSS im Adapter-Modul liegen, die Client-Stubs MÜSSEN per Codegen aus der WSDL erzeugt werden, und der Client MUSS als injizierbarer CXF-Client unter dem Namen „imperium" bereitstehen.

#### Scenario: Bestellung über die SOAP-Operation
- **WHEN** ein kanonischer Kaufauftrag übergeben wird
- **THEN** ruft der Adapter `BestelleSternenzerstoerer` mit auftragsReferenz, besteller, klassen-Code (IMPERIAL_I → ISD_I, IMPERIAL_II → ISD_II, VICTORY → VICTORY, EXECUTOR → EXECUTOR), stueckzahl und zielwelt auf

#### Scenario: Statusabfrage über die SOAP-Operation
- **WHEN** eine Statusabfrage mit einer AuftragsId übergeben wird
- **THEN** ruft der Adapter `AbfrageBestellstatus` mit der auftragsReferenz auf

### Requirement: Kanonische Klasse wird auf SOAP-Klassen-Code abgebildet
Der Adapter MUSS die Sternenzerstoererklasse wie folgt auf den SOAP-Parameter klasse abbilden: IMPERIAL_I → ISD_I, IMPERIAL_II → ISD_II, VICTORY → VICTORY, EXECUTOR → EXECUTOR.

#### Scenario: Abbildung aller vier Klassen
- **WHEN** eine Bestellung je Klasse IMPERIAL_I, IMPERIAL_II, VICTORY und EXECUTOR übergeben wird
- **THEN** enthält der SOAP-Aufruf den zugehörigen Klassen-Code ISD_I, ISD_II, VICTORY bzw. EXECUTOR

### Requirement: Zustellung liefert Zustellbestaetigung
Der Adapter MUSS als Zielsystem-Implementierung für den Typ IMPERIUM registriert sein. Bei erfolgreicher Bestellung MUSS er die gelieferte bestellnummer als externe Referenz in einer Zustellbestaetigung zurückgeben.

#### Scenario: Erfolgreiche Bestellung
- **WHEN** `zustellen` mit einem gültigen Kaufauftrag aufgerufen wird und die Imperiumswerft mit bestellnummer „ISD-4711" antwortet
- **THEN** liefert der Adapter eine Zustellbestaetigung mit der externen Referenz „ISD-4711"

### Requirement: Statusabfrage liefert kanonischen Verarbeitungsstatus
Der Adapter MUSS den SOAP-Status auf den kanonischen Verarbeitungsstatus abbilden (IN_BEARBEITUNG → IN_BEARBEITUNG, ABGESCHLOSSEN → ABGESCHLOSSEN, UNBEKANNT → UNBEKANNT) und die mitgelieferte bestellnummer als externe Referenz im Verarbeitungsstatus tragen, wenn vorhanden.

#### Scenario: Status in Bearbeitung
- **WHEN** `statusAbfragen` die SOAP-Antwort status IN_BEARBEITUNG mit bestellnummer „ISD-4711" erhält
- **THEN** liefert der Adapter den Verarbeitungsstatus IN_BEARBEITUNG mit externer Referenz „ISD-4711"

#### Scenario: Status abgeschlossen
- **WHEN** `statusAbfragen` die SOAP-Antwort status ABGESCHLOSSEN erhält
- **THEN** liefert der Adapter den Verarbeitungsstatus ABGESCHLOSSEN

### Requirement: Fehlervereinheitlichung mit 2-Sekunden-Timeout
Der Adapter MUSS jeden Misserfolg (SOAP-Fault, HTTP-Fehler, Timeout) als `ZustellungUngeklaert` melden. Er DARF KEINE Retries durchführen und DARF NICHT auf die Datenbank zugreifen. Der SOAP-Client MUSS mit einem Timeout von 2 Sekunden konfiguriert sein.

#### Scenario: SOAP-Fault wird zu ZustellungUngeklaert
- **WHEN** die Imperiumswerft mit einem SOAP-Fault antwortet
- **THEN** wirft der Adapter `ZustellungUngeklaert` und unternimmt keinen Wiederholungsversuch

#### Scenario: Timeout wird zu ZustellungUngeklaert
- **WHEN** die Imperiumswerft nicht innerhalb von 2 Sekunden antwortet
- **THEN** bricht der Adapter den Aufruf ab und wirft `ZustellungUngeklaert`

### Requirement: WireMock-Teststubs für die Imperiumswerft
Für die Integrationstests MUSS der lokale WireMock Stubs für die Imperiumswerft bereitstellen: einen Erfolgs-Stub (bestellnummer ISD-4711), einen Fehler-Stub, wenn der Käufer „Jar Jar" enthält, einen Verzögerungs-Stub (5 Sekunden), wenn der Käufer „Langsam" enthält, sowie ein Statusabfrage-Szenario, das zunächst IN_BEARBEITUNG und danach ABGESCHLOSSEN liefert.

#### Scenario: Erfolgs-Stub
- **WHEN** eine Bestellung gegen den WireMock-Stub gesendet wird
- **THEN** antwortet dieser mit bestellnummer „ISD-4711"

#### Scenario: Fehler-Stub für Jar Jar
- **WHEN** die Bestellung einen Käufer enthält, der „Jar Jar" umfasst
- **THEN** antwortet der Stub mit einem technischen Fehler (SOAP-Fault)

#### Scenario: Verzögerungs-Stub für Langsam
- **WHEN** die Bestellung einen Käufer enthält, der „Langsam" umfasst
- **THEN** verzögert der Stub die Antwort um 5 Sekunden

#### Scenario: Statusabfrage-Szenario
- **WHEN** die Statusabfrage erstmalig gegen den Stub gesendet wird
- **THEN** liefert dieser den Status IN_BEARBEITUNG
- **WHEN** die Statusabfrage erneut gesendet wird
- **THEN** liefert dieser den Status ABGESCHLOSSEN
