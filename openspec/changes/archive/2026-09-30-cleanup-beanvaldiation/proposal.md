## Why

Die Validierung ist aktuell doppelt und uneinheitlich implementiert: Die Modell-Records sichern ihre Wertebereiche per handgeschriebener Compact-Constructor-Prüfungen (`IllegalArgumentException`), während die REST-Annahme dieselben Regeln bereits per Bean Validation prüft. Dazu haben sich ungenutzte Imports angesammelt. Ein einheitlicher, deklarativer Weg über die Jakarta Bean Validation API entfernt die Redundanz und hält die Regeln an einem Ort.

## What Changes

- Ungenutzte Imports werden entfernt (4 Dateien: `OpenSearchIndexClient`, `AnnahmeIT`, `MaxVersucheZweiProfile`, `ImperiumUebersetzerTest`).
- Die Compact-Constructor-Prüfungen in den Modell-Records (`Kaufauftrag`, `AuftragsId`, `Zustellbestaetigung`) werden durch Bean-Validation-Constraints ersetzt. **BREAKING** für das Bibliotheksverhalten des Moduls: Records mit ungültigen Werten werden nicht mehr automatisch bei der Konstruktion abgewiesen, sondern erst dort, wo ein Validator läuft.
- Das Modul `raumschiffwerft-model` deklariert ausschließlich die Jakarta Bean Validation API (`jakarta.validation:jakarta.validation-api`), Scope `provided` — nichts Quarkus-Spezifisches; die Abhängigkeit landet nicht im kompilierten Artefakt und wird nicht transitiv weitergereicht.
- Die Zustell-Route `direct:zustellen` validiert den kanonischen `Kaufauftrag` per Camel bean-validator (`camel-quarkus-bean-validator`), bevor der Adapter angewählt wird. Ein Verstoß läuft wie jeder technische Misserfolg über `CamelZustellport` als `ZustellungUngeklaert` und wird als UNGEKLAERT verbucht (Backoff und Abgleich greifen wie gehabt).
- Die REST-Annahme validiert weiterhin ausschließlich per Bean Validation; der REST-Vertrag (400/200/202/404/503) bleibt unverändert.

## Capabilities

### New Capabilities

<!-- keine -->

### Modified Capabilities

- `kanonisches-modell`: Die Wertebereiche des Kaufauftrags werden deklarativ als Bean-Validation-Constraints ausgedrückt statt per Konstruktur-Prüfung abgewiesen; die Modell-Records tragen Constraints, das Modul darf die Jakarta Bean Validation API (provided) deklarieren.
- `zustellungssteuerung`: Die Zustell-Route validiert den kanonischen Auftrag per Bean Validation vor der Adapter-Anwahl; ein Verstoß wird wie ein technischer Misserfolg behandelt (UNGEKLAERT mit Backoff).
- `build-structure`: `raumschiffwerft-model` deklariert als einzige Fremd-Abhängigkeit `jakarta.validation-api` mit Scope `provided` (BOM-verwaltet); `raumschiffwerft-service` deklariert zusätzlich `camel-quarkus-bean-validator`.

## Impact

- **Code**: `raumschiffwerft-model` (Entities, pom, Unit-Tests), `raumschiffwerft-service` (`ZustellRoute`, pom, Tests), `raumschiffwerft-adapter-imperium` (Test-Imports).
- **Abhängigkeiten**: `raumschiffwerft-model` + `jakarta.validation-api` (provided); `raumschiffwerft-service` + `camel-quarkus-bean-validator` (Version aus `quarkus-camel-bom`).
- **Verhalten**: Der kanonische `Kaufauftrag` wird erst bei expliziter Validierung (Annahme bzw. Zustell-Route) geprüft, nicht mehr bei der Konstruktion; ungültige Aufträge erreichen die Adapter nicht. Der REST-Vertrag ändert sich nicht.
- **Tests**: Modell-Unit-Tests, die `IllegalArgumentException` erwarten, werden auf Constraint-Prüfung über einen Validator umgestellt; die bestehende 400-Semantik der Annahme-ITs bleibt unberührt.
