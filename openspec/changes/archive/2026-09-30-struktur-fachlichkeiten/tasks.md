## 1. Parent-POM und Build-Gerüst

- [x] 1.1 Parent-POM anlegen (groupId `org.kore.raumschiffwerft`, Java-Release 25, Aggregation der vier Module, BOM-Importe: quarkus-bom, quarkus-camel-bom, quarkus-cxf-bom, junit-bom, mockito-bom, verwaltete ArchUnit-Version; Plugin-Management: compiler, surefire, failsafe, jandex, quarkus; Surefire im Parent für alle Module) und verifizieren: `mvn -N validate` läuft durch
- [x] 1.2 Test-Abhängigkeiten im Parent verankern (JUnit 5 + Mockito als Test-Scope via BOM) und verifizieren: ein Probetest in einem Modul läuft via `mvn test` und wird danach entfernt

## 2. Module

- [x] 2.1 Modul `raumschiffwerft-model` anlegen (interne BCE-Packages `org.kore.raumschiffwerft.model.boundary/.control/.entity`, keine Projekt- und keine Framework-Abhängigkeiten) und verifizieren: Modul baut im Reactor
- [x] 2.2 Modul `raumschiffwerft-adapter-imperium` anlegen (BCE-Packages `org.kore.raumschiffwerft.adapter.imperium.boundary/.control/.entity`, Abhängigkeit nur auf model, Framework-Dependency `quarkus-cxf` für den SOAP-Aufruf via Jakarta-EE-APIs, KEINE Camel-Abhängigkeit, Jandex-Plugin, Quarkus-Maven-Plugin nur mit `generate-code`/`generate-code-tests`, `quarkus-junit` als Test-Abhängigkeit, Failsafe für `*IT`, kein `build`-Ziel) und verifizieren: Modul baut als Bibliotheks-JAR, `META-INF/jandex.idx` liegt im Artefakt, und ein temporärer `*IT` mit `@QuarkusTest` bootet die Adapter-Quarkus-App via Failsafe und wird danach wieder entfernt
- [x] 2.3 Modul `raumschiffwerft-adapter-rebellion` anlegen (BCE-Packages `org.kore.raumschiffwerft.adapter.rebellion.boundary/.control/.entity`, Abhängigkeit nur auf model, Framework-Dependencies `quarkus-rest-client` und `quarkus-rest-client-jackson` für den REST-Aufruf via Jakarta-REST-Client, KEINE Camel-Abhängigkeit, Jandex-Plugin, Quarkus-Maven-Plugin nur mit `generate-code`/`generate-code-tests`, `quarkus-junit` als Test-Abhängigkeit, Failsafe für `*IT`, kein `build`-Ziel) und verifizieren: Modul baut als Bibliotheks-JAR, `META-INF/jandex.idx` liegt im Artefakt, und ein temporärer `*IT` mit `@QuarkusTest` bootet die Adapter-Quarkus-App via Failsafe und wird danach wieder entfernt
- [x] 2.4 Modul `raumschiffwerft-service` anlegen (BCE-Packages `org.kore.raumschiffwerft.service.boundary/.control/.entity`, Abhängigkeiten auf beide Adapter, Framework-Dependencies `quarkus-rest`, `quarkus-rest-jackson`, `camel-quarkus-core`, Quarkus-Maven-Plugin mit build-Ziel, Failsafe für `*IT`, `native`-Profil, minimale Application-Klasse und `application.properties` mit devenv-Verbindungsdaten) und verifizieren: `mvn verify` baut alle vier Module grün und der service startet im Quarkus-dev-Modus gegen die laufende devenv-Infrastruktur

## 3. ArchUnit-Regeln

- [x] 3.1 ArchUnit als Test-Abhängigkeit im service ergänzen und Modul-Layering sowie Camel-Freiheit der Adapter implementieren (model -> nichts, Adapter -> nur model, keine `org.apache.camel..`-Referenzen in Adapter-Klassen); verifizieren: provozierte Verstöße (Adapter-Klasse referenziert service bzw. eine Camel-Klasse) lassen `mvn test` scheitern und werden danach entfernt
- [x] 3.2 Interne BCE-Regeln (entity -> kein control/boundary, control -> kein boundary) und Regel "kein `@QuarkusTest` auf Klassen `*Test`" implementieren; verifizieren: provozierte Verstöße (entity auf boundary, `*Test` mit `@QuarkusTest`) lassen den Build scheitern und werden danach entfernt

## 4. Gesamtabnahme

- [x] 4.1 End-to-End prüfen: `mvn verify` auf dem Parent grün (alle Module, Surefire + Failsafe + Jandex), `mvn dependency:tree` bestätigt den zyklenfreien Graphen (Adapter nur model, service aggregiert), service startet im dev-Modus gegen die devenv-Dienste — Ergebniskombination aus Spec `build-structure` und `module-architecture` bestätigt
