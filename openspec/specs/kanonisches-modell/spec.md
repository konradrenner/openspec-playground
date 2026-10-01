# Kanonisches-Modell Specification

## Purpose

Stellt das kanonische interne Datenmodell der Raumschiffwerft bereit (Wertobjekte, DTOs, checked Exception) sowie das Port-Interface `Zielsystem`, über das der Service Aufträge unabhängig vom konkreten Zielsystem zustellt und Status abfragt.

## Requirements

### Requirement: AuftragsId ist ein UUID-Wertobjekt
Eine AuftragsId MUSS eine UUID kapseln. Zwei AuftragsIds mit derselben UUID MÜSSEN als gleich gelten, zwei mit verschiedenen UUIDs als ungleich.

#### Scenario: Gleichheit anhand der UUID
- **WHEN** zwei AuftragsIds aus derselben UUID erzeugt werden
- **THEN** gelten sie als gleich und haben identische Hash-Codes

### Requirement: Sternenzerstoererklasse mit vier Werten
Die Sternenzerstoererklasse MUSS genau die Werte IMPERIAL_I, IMPERIAL_II, VICTORY und EXECUTOR annehmen können.

#### Scenario: Alle Klassenwerte nutzbar
- **WHEN** eine Sternenzerstoererklasse mit einem der vier Werte IMPERIAL_I, IMPERIAL_II, VICTORY oder EXECUTOR gebildet wird
- **THEN** ist diese gültig und trägt den jeweiligen Wert

### Requirement: Kaufauftrag mit Wertebereichen
Ein Kaufauftrag MUSS einen Käufer (Länge 1 bis 100 Zeichen), eine Sternenzerstoererklasse, eine Anzahl (1 bis 12) und einen Lieferplaneten (1 bis 60) umfassen. Die Wertebereiche MÜSSEN als Bean-Validation-Constraints am Kaufauftrag deklariert sein. Der Kaufauftrag DARF die Wertebereiche NICHT selbst bei der Konstruktion prüfen; die Abweisung ERFOLGT ausschließlich durch die Ausführung der Bean Validation an den Stellen, die den Auftrag verarbeiten.

#### Scenario: Gültiger Kaufauftrag
- **WHEN** ein Kaufauftrag mit Käufer „Mon Mothma", Klasse VICTORY, Anzahl 3 und Lieferplanet 42 erzeugt wird
- **THEN** trägt der Auftrag diese Werte

#### Scenario: Ungültige Werte werden abgelehnt
- **WHEN** ein Kaufauftrag mit leerem oder länger als 100 Zeichen langem Käufer, Anzahl 0 oder 13, oder Lieferplanet 0 oder 61 geprüft wird
- **THEN** weist die Bean Validation den Auftrag mit einer Verletzung ab, die den verletzten Wertebereich nennt

### Requirement: Modell-Records deklarieren Bean-Validation-Constraints
Die Wertobjekte und DTOs des kanonischen Modells MÜSSEN ihre Pflicht- und Wertebereichsregeln als Bean-Validation-Constraints deklarieren (z. B. NotNull auf der UUID der AuftragsId, NotBlank auf der externen Referenz der Zustellbestaetigung). Die Records DÜRFEN diese Regeln NICHT selbst im Konstruktor prüfen; die Abweisung ungültiger Werte ERFOLGT ausschließlich durch die Ausführung der Bean Validation an den Stellen, die das Objekt verarbeiten (Annahme bzw. Zustell-Route im Service).

#### Scenario: Konstruktion ohne Pruefung
- **WHEN** ein Record des Modells mit regelwidrigen Werten erzeugt wird (z. B. AuftragsId ohne UUID, Zustellbestaetigung mit leerer externer Referenz)
- **THEN** wirft die Konstruktion keine Validierungsausnahme; die Regelverletzung wird erst bei Ausführung der Bean Validation festgestellt

#### Scenario: Validator findet Regelverstoesse
- **WHEN** ein Validator ein regelwidriges Objekt des Modells prueft
- **THEN** meldet er fuer jede verletzte Constraint eine Verletzung mit dem betroffenen Wert

### Requirement: Zielsystemtyp Imperium oder Rebellion
Der Zielsystemtyp MUSS genau die Werte IMPERIUM und REBELLION annehmen können.

#### Scenario: Beide Zielsystemtypen
- **WHEN** der Zielsystemtyp IMPERIUM oder REBELLION gebildet wird
- **THEN** ist dieser gültig und trägt den jeweiligen Wert

### Requirement: Zustellbestaetigung mit externer Referenz
Eine Zustellbestaetigung MUSS die externe Referenz des Zielsystems (z. B. Bestellnummer oder BeschaffungsId) enthalten.

#### Scenario: Bestätigung trägt externe Referenz
- **WHEN** eine Zustellbestaetigung mit der externen Referenz „ISD-4711" erzeugt wird
- **THEN** macht diese die Referenz abrufbar

### Requirement: Verarbeitungsstatus mit optionaler externer Referenz
Der Verarbeitungsstatus MUSS die Werte ABGESCHLOSSEN, IN_BEARBEITUNG und UNBEKANNT annehmen können. Er MUSS optional eine externe Referenz tragen können.

#### Scenario: Status ohne externe Referenz
- **WHEN** ein Verarbeitungsstatus IN_BEARBEITUNG ohne externe Referenz gebildet wird
- **THEN** ist dieser gültig und hat keine Referenz

#### Scenario: Status mit externer Referenz
- **WHEN** ein Verarbeitungsstatus ABGESCHLOSSEN mit externer Referenz „RB-1138" gebildet wird
- **THEN** ist dieser gültig und macht die Referenz abrufbar

### Requirement: Port-Interface Zielsystem
Das Modell MUSS ein Port-Interface `Zielsystem` mit den Operationen `typ()` (liefert den Zielsystemtyp), `zustellen(AuftragsId, Kaufauftrag)` (liefert eine Zustellbestaetigung oder wirft `ZustellungUngeklaert`) und `statusAbfragen(AuftragsId)` (liefert einen Verarbeitungsstatus oder wirft `ZustellungUngeklaert`) bereitstellen. Die AuftragsId MUSS bereits bei der Zustellung übergeben werden, damit das Zielsystem den Auftrag unter dieser Referenz anlegen kann und `statusAbfragen` dieselbe Referenz wiederfindet.

#### Scenario: Zustellung erfolgreich
- **WHEN** `zustellen` mit einer AuftragsId und einem gültigen Kaufauftrag aufgerufen wird und das Zielsystem den Auftrag annimmt
- **THEN** liefert die Operation eine Zustellbestaetigung mit der externen Referenz des Zielsystems

#### Scenario: Statusabfrage erfolgreich
- **WHEN** `statusAbfragen` mit einer AuftragsId aufgerufen wird und das Zielsystem einen Status liefert
- **THEN** liefert die Operation den zugehörigen Verarbeitungsstatus

### Requirement: Checked Exception ZustellungUngeklaert
`ZustellungUngeklaert` MUSS eine checked Exception sein, die einen Misserfolg der Zustellung oder Statusabfrage beschreibt. Jede Implementierung von `Zielsystem` MUSS jeden Misserfolg (Nicht-2xx-Antwort, SOAP-Fault, Timeout) als `ZustellungUngeklaert` melden. Implementierungen DÜRFEN KEINE automatischen Wiederholungsversuche durchführen und DÜRFEN NICHT auf die Datenbank zugreifen.

#### Scenario: Technischer Misserfolg wird vereinheitlicht
- **WHEN** ein Aufruf des Zielsystems mit einem technischen Misserfolg (HTTP-Fehlerstatus, SOAP-Fault oder Timeout) endet
- **THEN** wirft die Implementierung `ZustellungUngeklaert` statt einer technischen Ausnahme des Zielsystems

#### Scenario: Keine Retries
- **WHEN** ein Aufruf des Zielsystems fehlschlägt
- **THEN** unternimmt die Implementierung keinen weiteren Zustellversuch für denselben Auftrag

### Requirement: Modell ohne Framework-Abhängigkeiten
Das kanonische Modell DARF KEINE Abhängigkeit auf Frameworks (Quarkus, Camel, CXF, JSON-Bindung) haben; es besteht nur aus DTOs, Wertobjekken, der Exception und dem Port-Interface. Das Modell DARF ausschließlich die Jakarta Bean Validation API deklarieren — mit Scope `provided`, sodass sie weder im kompilierten Artefakt des Moduls landet noch transitiv an Adapter oder Service weitergereicht wird; sie dient nur dem Kompilieren der Constraints.

#### Scenario: Framework-Klasse im Modell wird abgelehnt
- **WHEN** eine Klasse des Modell-Moduls auf eine Framework-Klasse referenziert
- **THEN** scheitert die Prüfung der Modulabhängigkeiten (Build bzw. Architekturtest)

#### Scenario: Bean-Validation-API ist erlaubt und bleibt aussen vor
- **WHEN** das Modell-Modul gebaut wird
- **THEN** kompiliert es gegen die Jakarta Bean Validation API, ohne dass diese im Artefakt des Moduls oder in den Abhängigkeitsbäumen der Adapter auftaucht
