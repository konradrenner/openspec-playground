## 1. Ungenutzte Imports entfernen

- [x] 1.1 In `raumschiffwerft-service` main: ungenutzten Import `jakarta.enterprise.context.ApplicationScoped` aus `OpenSearchIndexClient.java` entfernen und Kompilation via `mvn -pl raumschiffwerft-service compile` pruefen
- [x] 1.2 In `raumschiffwerft-service` test: ungenutzten Import `TestMethodOrder` aus `AnnahmeIT.java` und die 6 unbenutzten Imports (java.sql.*, OffsetDateTime, UUID) aus `MaxVersucheZweiProfile.java` entfernen und `mvn -pl raumschiffwerft-service test-compile` pruefen
- [x] 1.3 In `raumschiffwerft-adapter-imperium` test: ungenutzten Import `Zustellbestaetigung` aus `ImperiumUebersetzerTest.java` entfernen und `mvn -pl raumschiffwerft-adapter-imperium test-compile` pruefen

## 2. Bean Validation im Model einfuehren

- [x] 2.1 In `raumschiffwerft-model/pom.xml` die Abhaengigkeit `jakarta.validation:jakarta.validation-api` mit Scope `provided` deklarieren (keine Version, Verwaltung via quarkus-bom aus dem Parent); danach `mvn -pl raumschiffwerft-model clean verify` und Pruefung, dass `dependency:tree` die API nur als provided zeigt
- [x] 2.2 In `Kaufauftrag` die Compact-Constructor-Pruefungen entfernen und Constraints anlegen: `@NotBlank @Size(min = 1, max = 100)` kaeufer, `@NotNull` klasse, `@Min(1) @Max(12)` anzahl, `@Min(1) @Max(60)` lieferplanet; in `AuftragsId` Null-Check durch `@NotNull` auf wert ersetzen; in `Zustellbestaetigung` Leer-Check durch `@NotBlank` auf externeReferenz ersetzen
- [x] 2.3 Fuer die Constraint-Pruefung `org.hibernate.validator:hibernate-validator` (BOM-verwaltet, Scope `test`) im Model deklarieren und die Modell-Tests umschreiben: statt `IllegalArgumentException`-Erwartungen Validator-Verstoesse asserten (Vorlage: `KaufauftragAnfrageTest` im service); `mvn -pl raumschiffwerft-model verify` muss gruen laufen
- [x] 2.4 Pruefen, dass die Adapter-Module ohne eigene jakarta.validation-Abhaengigkeit kompilieren: `mvn -pl raumschiffwerft-adapter-imperium,raumschiffwerft-adapter-rebellion verify` (dependency:tree der Adapter zeigt keine Bean-Validation-API)

## 3. Bean Validation in der Zustell-Route

- [x] 3.1 In `raumschiffwerft-service/pom.xml` `camel-quarkus-bean-validator` deklarieren (Version aus quarkus-camel-bom, mit Kommentar an die bestehenden Camel-Abhaengigkeiten) und `mvn -pl raumschiffwerft-service compile` ausfuehren
- [x] 3.2 `ZustellRoute` erweitern: eingangs in `direct:zustellen` den Body (kanonischer `Kaufauftrag`) per bean-validator pruefen, VOR dem choice nach Zielsystemtyp; Verifizieren, dass `CamelZustellport` eine `BeanValidationException` als `ZustellungUngeklaert` uebersetzt (Unit-Test mit ProducerTemplate bzw. Anpassung `CamelZustellportTest`)
- [x] 3.3 Unit-Test ergaenzen, der einen regelverletzenden kanonischen `Kaufauftrag` durch die Route schickt und assertet, dass kein Adapter gerufen wird und `ZustellungUngeklaert` gemeldet wird (Mock der Adapter); `mvn -pl raumschiffwerft-service test` muss gruen laufen

## 4. Abschlusspruefung

- [x] 4.1 Gesamtbau: `mvn verify` in der devenv-Shell ueber alle vier Module; Architekturtests (Layering, BCE, Camel-Freiheit der Adapter) laufen mit und bleiben gruen
- [x] 4.2 Bestehende Annahme-ITs laufen unveraendert (400-Semantik fuer ungueltige Bodies bleibt): `mvn -pl raumschiffwerft-service verify -DskipTests=false` bzw. Failsafe-Ausfuehrung mit laufender devenv-Infrastruktur
- [x] 4.3 `openspec validate cleanup-beanvaldiation` ohne --strict ausfuehren und Fehler beheben
