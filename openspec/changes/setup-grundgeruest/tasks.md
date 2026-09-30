## 1. devenv.nix: JDK, Maven und Dienste

- [x] 1.1 devenv.nix um JDK 25 (`languages.java`) und Maven (`packages`) erweitern und verifizieren: In der devenv-Shell liefern `java --version` Version 25 und `mvn --version` eine aktuelle Maven-Version
- [x] 1.2 devenv.nix um `services.postgres` (Datenbank `raumschiffwerft`, User `raumschiffwerft`), `services.kafka` und `services.opensearch` erweitern und verifizieren: `devenv up` bringt alle drei Dienste hoch, Verbindung als User `raumschiffwerft` auf Datenbank `raumschiffwerft` klappt
- [x] 1.3 OTel-Collector-Konfiguration `otelcol/config.yaml` anlegen (OTLP-Receiver 4317 gRPC / 4318 HTTP, Debug-Exporter, health_check-Extension) und als devenv-`services.opentelemetry-collector` (configFile) einbinden; verifiziert: Telemetrie an 4318 erscheint lesbar in der Prozess-Ausgabe
- [x] 1.4 WireMock als devenv-`services.wiremock` auf Port 8089 mit `rootDir wiremock` einbinden und Verzeichnis `wiremock/mappings/` anlegen; verifiziert: eine Beispiel-Stub-Datei wird bedient und eine nachträglich ergänzte Datei ohne Änderung an devenv.nix ebenfalls
- [x] 1.5 GraalVM CE 25 als JDK nutzen (inkl. `native-image`); verifiziert: `java --version` meldet GraalVM CE 25.2.4, `native-image --version` verfügbar

> Hinweis (Revision, nutzerinitiiert): Der Java-Grundgerüst-Teil dieses Changes (Module,
> Build, ArchUnit) wurde nach der Loeschung des generierten Skeletts in den Change
> `struktur-fachlichkeiten` ueberfuehrt und wird dort Schritt fuer Schritt mit der
> Fachlichkeit Raumschiffwerft neu geplant.
