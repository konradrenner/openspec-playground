## Context

Siehe proposal.md für die Motivation. Ist-Stand: Die Modell-Records `Kaufauftrag`, `AuftragsId` und `Zustellbestaetigung` prüfen ihre Regeln per handgeschriebenen Compact-Constructor-Checks (`IllegalArgumentException`). Die REST-Annahme validiert die `KaufauftragAnfrage` bereits per Bean Validation (injizierter `jakarta.validation.Validator`, `quarkus-hibernate-validator` im service). Die Zustell-Route `direct:zustellen` (`ZustellRoute`) wählt per choice den Adapter, ohne den Body zu prüfen; `CamelZustellport` übersetzt jeden technischen Misserfolg in `ZustellungUngeklaert`. Constraints: modulares Layering model <- adapter <- service, Adapter Camel-frei, Versionen ausschließlich aus den im Parent importierten BOMs (Quarkus Platform 3.33.x), KISS.

## Goals / Non-Goals

**Goals:**

- Validierung der Fachregeln ausschließlich über die Jakarta Bean Validation API — deklarativ am Modell, ausgeführt im Service (Annahme und Zustell-Route).
- Model kompiliert gegen `jakarta.validation-api`, ohne dass die API im Artefakt landet oder transitiv weitergereicht wird.
- Ungenutzte Imports in main und test aller Module entfernt.

**Non-Goals:**

- Keine Änderung des REST-Vertrags (Statuscodes, Fehlermeldungen der Annahme bleiben wie in auftragsannahme spezifiziert).
- Keine Einführung von Bean Validation in den Adaptern (sie übersetzen nur; die Zustell-Route garantiert regelkonforme Aufträge).
- Keine Migration von `IllegalArgumentException`-Checks im Service-Aggregat `Kaufauftrag` (service entity, Zustandsmaschine) — nur das kanonische Modell ist Gegenstand des Changes.

## Decisions

### 1. Constraints statt Konstruktor-Checks im Model

`Kaufauftrag` erhält `@NotBlank @Size(min = 1, max = 100)` für kaeufer, `@NotNull` für klasse, `@Min(1) @Max(12)` für anzahl, `@Min(1) @Max(60)` für lieferplanet; `AuftragsId` erhält `@NotNull` auf wert; `Zustellbestaetigung` erhält `@NotBlank` auf externeReferenz. Die Compact-Constructor-Prüfungen entfallen vollständig (Entscheidung des Nutzers: Ersetzen, nicht Ergänzen).

- Alternative: Checks als Sicherheitsnetz behalten — verworfen, weil die Regeln dann doppelt gepflegt werden müssen und der Change genau die Redundanz beseitigen soll.
- Folgerung: `Sternenzerstoererklasse.valueOf(...)` in `KaufauftraegeResource` bleibt die Quelle für die Enum-Umwandlung (fremde Klasse-Namen würden weiterhin über das `@Pattern` auf der `KaufauftragAnfrage` abgewiesen).

### 2. Abhängigkeit im Model: jakarta.validation-api, scope provided

`raumschiffwerft-model/pom.xml` deklariert `jakarta.validation:jakarta.validation-api` mit `<scope>provided</scope>`, Version verwaltet durch den bestehenden `quarkus-bom`-Import des Parent-POMs (kein Quarkus-Artefakt im Model). Provided sorgt dafür, dass die API weder im Modell-Artefakt noch transitiv bei Adaptern/Service auftaucht. Zur Laufzeit bringt der Service die API samt Implementierung über `quarkus-hibernate-validator` mit; Hibernate Validator liest die Constraint-Annotationen per Reflexion aus den Modell-Klassen.

- Alternative: compile-Scope — verworfen, da die API dann in jedem Abhängigkeitsbaum der Adapter auftauchen würde, obwohl niemand sie dort braucht.
- Für die Modell-Unit-Tests der Constraints wird `org.hibernate.validator:hibernate-validator` (BOM-verwaltet) zusätzlich im Scope `test` deklariert — nur Testmaterial, landet nicht im Artefakt.

### 3. Camel bean-validator in der Zustell-Route

Camel kann Bean Validation: Die Extension `camel-quarkus-bean-validator` (Component `bean-validator`, im `quarkus-camel-bom` verwaltet) nutzt den Jakarta-Validator der Quarkus-Anwendung. `ZustellRoute` validiert daher eingangs in `direct:zustellen` den Body (kanonischer `Kaufauftrag`) per bean-validator, vor dem choice. Ein Verstoß wirft Camels `BeanValidationException`; `CamelZustellport` übersetzt sie wie jeden Fehler des Exchanges in `ZustellungUngeklaert` — die Zustellungssteuerung verbucht UNGEKLAERT mit Backoff (Anschluss an die bestehende Abgleich-Mechanik, kein Sonderweg).

- Alternative: Validierung nur in der REST-Boundary belassen — verworfen (Nutzerentscheidung „Beides“): der kanonische Auftrag wird auch aus der Abgleich-Route (Neuversand nach UNBEKANNT) über `direct:zustellen` geschickt, und ohne Route-Validierung wäre nach Wegfall der Konstruktor-Checks dort kein Prüfpunkt.
- Alternative: manuelles `validator.validate(...)` im Processor — verworfen, da Camel die Prüfung deklarativ anbietet und der Change die Handprüfungen eliminieren soll.

### 4. Annahme-Validierung bleibt, wie sie ist

`KaufauftraegeResource` prüft weiter den Idempotency-Key per Hand (UUID-Parsing ist keine Constraint-Regel, sondern Header-Verarbeitung mit eigener 400-Meldung) und den Body per injiziertem Validator. Eine Umstellung auf automatische Methoden-Parameter-Validierung (`@Valid`) würde die 400-Fehlerkörper ändern und ist REST-Vertrags-Änderung ohne Nutzen — nicht Teil des Changes.

### 5. Ungenutzte Imports: minimaler Hotfix, kein Refactoring

Entfernung exakt der verifizierten Funde (je Datei nur Import-Zeilen löschen, kein Umformatieren): `OpenSearchIndexClient` (`jakarta.enterprise.context.ApplicationScoped`), `AnnahmeIT` (`TestMethodOrder`), `MaxVersucheZweiProfile` (6 Imports), `ImperiumUebersetzerTest` (`Zustellbestaetigung`).

## Risks / Trade-offs

- [Verlust des Konstruktur-Sicherheitsnetzes: Regelwidrige Records entstehen nun ohne sofortige Ausnahme] → Zwei Prüfpunkte in der Verarbeitungskette (Annahme, Zustell-Route) weisen sie ab, bevor Daten das System verlassen oder persistiert werden; der Repository-Pfad (Rekonstruktion aus der DB) liefert weiterhin nur Werte, die bei der Annahme validiert wurden.
- [Konsumierende Module könnten die Annotationen versehentlich als API nutzen] → Provided-Scope macht die API nicht transitiv verfügbar; ein Verbraucher müsste sie bewusst selbst deklarieren.
- [`camel-quarkus-bean-validator` ist lokal noch nicht im Maven-Cache] → Beim ersten Build über devenv/nix wird sie gezogen; Version kommt aus dem `quarkus-camel-bom`, kein Versionseintrag im Modul.
- [Model-Tests müssen von `IllegalArgumentException` auf Validator-Assertions umgestellt werden] → Test-Abhängigkeit hibernate-validator (test) im Model; Erwartung: keine Testverschiebungen in Adapter oder Service.

## Migration Plan

Reiner Code-Change ohne Daten- oder Konfigurationsmigration; ein Build + `mvn verify` (devenv-Shell) genügt. Rollback ist ein Revert des Commits — es existieren keine persistenten Datenstrukturen, die von der Änderung abhängen.
