## 1. devenv.nix: JDK, Maven und Dienste

- [x] 1.1 devenv.nix um JDK 25 (`languages.java`) und Maven (`packages`) erweitern und verifizieren: In der devenv-Shell liefern `java --version` Version 25 und `mvn --version` eine aktuelle Maven-Version
- [x] 1.2 devenv.nix um `services.postgres` (Datenbank `durchlauferhitzer`, User `durchlauferhitzer`), `services.kafka` und `services.opensearch` erweitern und verifizieren: `devenv up` bringt alle drei Dienste hoch, Verbindung als User `durchlauferhitzer` auf Datenbank `durchlauferhitzer` klappt
- [x] 1.3 OTel-Collector-Konfiguration `otelcol/config.yaml` anlegen (OTLP-Receiver 4317 gRPC / 4318 HTTP, Debug-Exporter, health_check-Extension) und als devenv-`services.opentelemetry-collector` (configFile) einbinden; verifiziert: Telemetrie an 4318 erscheint lesbar in der Prozess-Ausgabe
- [x] 1.4 WireMock als devenv-`services.wiremock` auf Port 8089 mit `rootDir wiremock` einbinden und Verzeichnis `wiremock/mappings/` anlegen; verifiziert: eine Beispiel-Stub-Datei wird bedient und eine nachträglich ergänzte Datei ohne Änderung an devenv.nix ebenfalls

## 2. Maven-Grundgerüst

- [x] 2.1 Parent-POM (groupId `org.kore.durchlauferhitzer`, Java-Release 25, Module-Aggregation der vier Module, BOM-Importe: Quarkus-Plattform und Test-BOMs) anlegen und verifizieren: `mvn -N validate` auf dem Parent läuft durch
- [x] 2.2 Modul `durchlauferhitzer-domain` anlegen (leeres Package `org.kore.durchlauferhitzer.domain`, keine Projektabhängigkeiten) und verifizieren: Modul baut als Teil des Parent-Reactor
- [x] 2.3 Modul `durchlauferhitzer-application` anlegen (Package `org.kore.durchlauferhitzer.application`, Abhängigkeit nur auf `durchlauferhitzer-domain`) und verifizieren: Modul baut im Reactor
- [x] 2.4 Modul `durchlauferhitzer-adapters` anlegen (Package `org.kore.durchlauferhitzer.adapters`, Abhängigkeiten auf `domain` und `application`, Quarkus-Jandex-Plugin mit `jandex`-Ziel) und verifizieren: Build erzeugt `META-INF/jandex.idx` im Artefakt
- [x] 2.5 Surefire im Parent-POM konfigurieren (Unit-Tests `*Test`, JUnit 5 + Mockito als Test-Abhängigkeiten via BOM) und verifizieren: ein einfacher Probetest in jedem Modul läuft via `mvn test` und wird danach wieder entfernt bzw. durch die echten ArchUnit-Tests ersetzt
- [x] 2.6 Modul `durchlauferhitzer-service` anlegen: Abhängigkeit auf `adapters` (und damit transitiv auf alle Module), Failsafe-Plugin (Integrationstests `*IT`), Quarkus-Build-Plugins; verifizieren: `mvn verify` baut alle vier Module grün

## 3. Leere Quarkus-App

- [x] 3.1 Minimale Application-Klasse im Modul `durchlauferhitzer-service` (Package `org.kore.durchlauferhitzer.service`) plus `application.properties` mit den devenv-Verbindungsdaten (Postgres/Kafka) anlegen; verifizieren: App startet im Quarkus-dev-Modus mit laufendem `devenv up` ohne Konfigurationsfehler

## 4. ArchUnit-Regeln

- [x] 4.1 ArchUnit als Test-Abhängigkeit in `durchlauferhitzer-service` ergänzen und Schichtungsregeln implementieren (`domain` -> nichts, `application` -> nur `domain`, `adapters` -> nur `domain`/`application`); verifizieren: `mvn verify` lässt die Regeln mitlaufen und ein absichtlich provozierter Verstoß (z. B. Test-Referenz von domain auf application) lässt den Build scheitern
- [x] 4.2 ArchUnit-Regel "kein `@QuarkusTest` auf Klassen `*Test`" implementieren; verifizieren: eine provisorische Klasse `*Test` mit `@QuarkusTest` lässt die Regel scheitern und wird danach entfernt

## 5. Gesamtabnahme

- [x] 5.1 End-to-End prüfen: `devenv up` startet alle Dienste (Kafka, Postgres, OpenSearch, OTel-Collector, WireMock auf 8089), danach `mvn verify` auf dem Parent grün, Quarkus-App startet gegen die Dienste — Ergebniskombination aus Spec `local-dev-environment`, `build-structure` und `module-architecture` bestätigt

## 6. Revision: BCE-Modulstruktur und devenv-Services (nutzerinitiiert)

- [x] 6.1 Module umbauen auf `durchlauferhitzer-model` (kanonisches Modell), `durchlauferhitzer-adapter-rest`, `durchlauferhitzer-adapter-soap` und `durchlauferhitzer-service`, jeweils intern paketiert nach boundary/control/entity; verifiziert: `mvn verify` baut alle vier Module grün
- [x] 6.2 ArchUnit-Regeln anpassen: Modul-Layering (model <- adapter <- service) und interne BCE-Regeln (entity -> nichts nach außen, control -> kein boundary); verifiziert: provozierte Verstöße (Adapter auf service, entity und control auf boundary) lassen den Build mit klarer Meldung scheitern, danach wieder grün
- [x] 6.3 GraalVM CE 25 als JDK nutzen und native Kompilierung ermöglichen (`native`-Profil im Modul service); verifiziert: `java --version` meldet GraalVM CE 25.2.4, `native-image --version` verfügbar
- [x] 6.4 OTel-Collector und WireMock als devenv-Services statt manuelle Prozesse einbinden; verifiziert: alle Prozesse `ready`, OTLP-Test-Event im Debug-Exporter, WireMock bedient Stubs aus `wiremock/mappings`
