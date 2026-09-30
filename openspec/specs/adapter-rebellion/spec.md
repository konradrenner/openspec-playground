# Adapter-Rebellion Specification

## Purpose

Setzt das Port-Interface `Zielsystem` für die Rebellion um: Übersetzung kanonischer Aufträge in REST-Aufrufe (Beschaffung anlegen und Status abfragen) gegen die Beschaffungs-API der Rebellion, inklusive einheitlicher Fehlerbehandlung und WireMock-Teststubs für die Integrationstests.

## Requirements

### Requirement: REST-Vertrag der Rebellen-Beschaffung
Der Adapter MUSS die Beschaffungs-API der Rebellion wie folgt aufrufen: `POST /api/v1/beschaffungen` mit dem JSON-Körper {referenz, auftraggeber, schiffstyp imperial-1/imperial-2/victory/executor, menge, zielplanet} und der Antwort {beschaffungsId, status} sowie `GET /api/v1/beschaffungen/{referenz}/status` mit der Antwort IN_ARBEIT/ERLEDIGT/UNBEKANNT. Der Client MUSS als MicroProfile-REST-Client bereitstehen.

#### Scenario: Beschaffung anlegen
- **WHEN** ein kanonischer Kaufauftrag übergeben wird
- **THEN** sendet der Adapter `POST /api/v1/beschaffungen` mit referenz, auftraggeber, schiffstyp, menge und zielplanet

#### Scenario: Status abfragen
- **WHEN** eine Statusabfrage mit einer AuftragsId übergeben wird
- **THEN** ruft der Adapter `GET /api/v1/beschaffungen/{referenz}/status` mit der Referenz auf

### Requirement: Kanonische Klasse wird auf Schiffstyp abgebildet
Der Adapter MUSS die Sternenzerstoererklasse wie folgt auf den JSON-Parameter schiffstyp abbilden: IMPERIAL_I → imperial-1, IMPERIAL_II → imperial-2, VICTORY → victory, EXECUTOR → executor.

#### Scenario: Abbildung aller vier Klassen
- **WHEN** eine Beschaffung je Klasse IMPERIAL_I, IMPERIAL_II, VICTORY und EXECUTOR übergeben wird
- **THEN** enthält der Request-Body den zugehörigen Schiffstyp imperial-1, imperial-2, victory bzw. executor

### Requirement: Zustellung liefert Zustellbestaetigung
Der Adapter MUSS als Zielsystem-Implementierung für den Typ REBELLION registriert sein. Bei erfolgreicher Beschaffung MUSS er die gelieferte beschaffungsId als externe Referenz in einer Zustellbestaetigung zurückgeben.

#### Scenario: Erfolgreiche Beschaffung
- **WHEN** `zustellen` mit einem gültigen Kaufauftrag aufgerufen wird und die Rebellion mit beschaffungsId „RB-1138" antwortet
- **THEN** liefert der Adapter eine Zustellbestaetigung mit der externen Referenz „RB-1138"

### Requirement: Statusabfrage liefert kanonischen Verarbeitungsstatus
Der Adapter MUSS den REST-Status auf den kanonischen Verarbeitungsstatus abbilden: IN_ARBEIT → IN_BEARBEITUNG, ERLEDIGT → ABGESCHLOSSEN, UNBEKANNT → UNBEKANNT.

#### Scenario: Status in Arbeit
- **WHEN** `statusAbfragen` die Antwort IN_ARBEIT erhält
- **THEN** liefert der Adapter den Verarbeitungsstatus IN_BEARBEITUNG

#### Scenario: Status erledigt
- **WHEN** `statusAbfragen` die Antwort ERLEDIGT erhält
- **THEN** liefert der Adapter den Verarbeitungsstatus ABGESCHLOSSEN

### Requirement: 404 wird als UNBEKANNT interpretiert
Der Adapter MUSS eine 404-Antwort der Statusabfrage als Verarbeitungsstatus UNBEKANNT interpretieren und KEINEN technischen Fehler melden.

#### Scenario: Unbekannte Referenz
- **WHEN** `statusAbfragen` mit einer Referenz aufgerufen wird, die die Rebellion nicht kennt, und die API mit 404 antwortet
- **THEN** liefert der Adapter den Verarbeitungsstatus UNBEKANNT

### Requirement: Fehlervereinheitlichung mit 2-Sekunden-Timeout
Der Adapter MUSS jeden Misserfolg (Nicht-2xx-Antwort außer 404 bei der Statusabfrage, Timeout) als `ZustellungUngeklaert` melden. Er DARF KEINE Retries durchführen und DARF NICHT auf die Datenbank zugreifen. Der REST-Client MUSS mit einem Timeout von 2 Sekunden konfiguriert sein.

#### Scenario: HTTP-Fehler wird zu ZustellungUngeklaert
- **WHEN** die Rebellen-API mit einem Fehlerstatus (z. B. 500) antwortet
- **THEN** wirft der Adapter `ZustellungUngeklaert` und unternimmt keinen Wiederholungsversuch

#### Scenario: Timeout wird zu ZustellungUngeklaert
- **WHEN** die Rebellen-API nicht innerhalb von 2 Sekunden antwortet
- **THEN** bricht der Adapter den Aufruf ab und wirft `ZustellungUngeklaert`

### Requirement: WireMock-Teststubs für die Rebellen-API
Für die Integrationstests MUSS der lokale WireMock Stubs für die Rebellen-API bereitstellen: einen Erfolgs-Stub (beschaffungsId RB-1138), einen Fehler-Stub, wenn der Käufer „Jar Jar" enthält, einen Verzögerungs-Stub (5 Sekunden), wenn der Käufer „Langsam" enthält, sowie ein Statusabfrage-Szenario, das zunächst IN_ARBEIT und danach ERLEDIGT liefert.

#### Scenario: Erfolgs-Stub
- **WHEN** eine Beschaffung gegen den WireMock-Stub gesendet wird
- **THEN** antwortet dieser mit beschaffungsId „RB-1138"

#### Scenario: Fehler-Stub für Jar Jar
- **WHEN** die Beschaffung einen Käufer enthält, der „Jar Jar" umfasst
- **THEN** antwortet der Stub mit einem Fehlerstatus

#### Scenario: Verzögerungs-Stub für Langsam
- **WHEN** die Beschaffung einen Käufer enthält, der „Langsam" umfasst
- **THEN** verzögert der Stub die Antwort um 5 Sekunden

#### Scenario: Statusabfrage-Szenario
- **WHEN** die Statusabfrage erstmalig gegen den Stub gesendet wird
- **THEN** liefert dieser den Status IN_ARBEIT
- **WHEN** die Statusabfrage erneut gesendet wird
- **THEN** liefert dieser den Status ERLEDIGT
