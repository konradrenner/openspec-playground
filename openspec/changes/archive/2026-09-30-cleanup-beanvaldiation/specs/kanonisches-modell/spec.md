# Kanonisches-Modell Specification (Delta)

## ADDED Requirements

### Requirement: Modell-Records deklarieren Bean-Validation-Constraints
Die Wertobjekte und DTOs des kanonischen Modells MÜSSEN ihre Pflicht- und Wertebereichsregeln als Bean-Validation-Constraints deklarieren (z. B. NotNull auf der UUID der AuftragsId, NotBlank auf der externen Referenz der Zustellbestaetigung). Die Records DÜRFEN diese Regeln NICHT selbst im Konstruktor prüfen; die Abweisung ungültiger Werte ERFOLGT ausschließlich durch die Ausführung der Bean Validation an den Stellen, die das Objekt verarbeiten (Annahme bzw. Zustell-Route im Service).

#### Scenario: Konstruktion ohne Pruefung
- **WHEN** ein Record des Modells mit regelwidrigen Werten erzeugt wird (z. B. AuftragsId ohne UUID, Zustellbestaetigung mit leerer externer Referenz)
- **THEN** wirft die Konstruktion keine Validierungsausnahme; die Regelverletzung wird erst bei Ausführung der Bean Validation festgestellt

#### Scenario: Validator findet Regelverstoesse
- **WHEN** ein Validator ein regelwidriges Objekt des Modells prueft
- **THEN** meldet er fuer jede verletzte Constraint eine Verletzung mit dem betroffenen Wert

## MODIFIED Requirements

### Requirement: Kaufauftrag mit Wertebereichen
Ein Kaufauftrag MUSS einen Käufer (Länge 1 bis 100 Zeichen), eine Sternenzerstoererklasse, eine Anzahl (1 bis 12) und einen Lieferplaneten (1 bis 60) umfassen. Die Wertebereiche MÜSSEN als Bean-Validation-Constraints am Kaufauftrag deklariert sein. Der Kaufauftrag DARF die Wertebereiche NICHT selbst bei der Konstruktion prüfen; die Abweisung ERFOLGT ausschließlich durch die Ausführung der Bean Validation an den Stellen, die den Auftrag verarbeiten.

#### Scenario: Gültiger Kaufauftrag
- **WHEN** ein Kaufauftrag mit Käufer „Mon Mothma", Klasse VICTORY, Anzahl 3 und Lieferplanet 42 erzeugt wird
- **THEN** trägt der Auftrag diese Werte

#### Scenario: Ungültige Werte werden abgelehnt
- **WHEN** ein Kaufauftrag mit leerem oder länger als 100 Zeichen langem Käufer, Anzahl 0 oder 13, oder Lieferplanet 0 oder 61 geprüft wird
- **THEN** weist die Bean Validation den Auftrag mit einer Verletzung ab, die den verletzten Wertebereich nennt

### Requirement: Modell ohne Framework-Abhängigkeiten
Das kanonische Modell DARF KEINE Abhängigkeit auf Frameworks (Quarkus, Camel, CXF, JSON-Bindung) haben; es besteht nur aus DTOs, Wertobjekten, der Exception und dem Port-Interface. Das Modell DARF ausschließlich die Jakarta Bean Validation API deklarieren — mit Scope `provided`, sodass sie weder im kompilierten Artefakt des Moduls landet noch transitiv an Adapter oder Service weitergereicht wird; sie dient nur dem Kompilieren der Constraints.

#### Scenario: Framework-Klasse im Modell wird abgelehnt
- **WHEN** eine Klasse des Modell-Moduls auf eine Framework-Klasse referenziert
- **THEN** scheitert die Prüfung der Modulabhängigkeiten (Build bzw. Architekturtest)

#### Scenario: Bean-Validation-API ist erlaubt und bleibt aussen vor
- **WHEN** das Modell-Modul gebaut wird
- **THEN** kompiliert es gegen die Jakarta Bean Validation API, ohne dass diese im Artefakt des Moduls oder in den Abhängigkeitsbäumen der Adapter auftaucht
