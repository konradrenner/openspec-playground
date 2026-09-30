## 1. Abhaengigkeiten und Konfiguration

- [x] 1.1 Service-POM erweitern: `camel-quarkus-timer` (Timer-Route) und `org.awaitility:awaitility` mit Scope test — verifiziert durch erfolgreiches `mvn -pl raumschiffwerft-service -am compile` in der devenv-Shell
- [x] 1.2 Konfiguration in `application.properties`: `zustellung.lease-minuten` durch `zustellung.lease-sekunden` (Default 600) ersetzen, neue Properties `abgleich.timer-period-millis` (10000), `abgleich.batch` (20), `abgleich.backoff-max-sekunden` (3600), `abgleich.jitter-anteil` (0.2), `abgleich.max-versuche` (5) anlegen — verifiziert durch Start des Service (`devenv up`-Umgebung, Flyway laeuft, Timer-Route registriert)
- [x] 1.3 `%test`-Profil in `src/test/resources/application.properties` mit kurzen Werten (Timer ~500 ms, Lease ~2 s, Backoff-Basis ~1 s, max-versuche 3 — 3 statt 2, weil der Imperium-Status-Stub beim ersten Abfrage IN_BEARBEITUNG liefert und der Weg bis BESTAETIGT zwei Beanspruchungen braucht) — verifiziert durch schnelles Konvergieren der neuen ITs (unter 10 s je Test)

## 2. Zustandsmaschine und Backoff (entity)

- [x] 2.1 `Zustellungsstatus` um erlaubte Folgezustaende erweitern und `Zustellung` um `beanspruchen(leaseBis, instanz)` (von IN_ZUSTELLUNG/UNGEKLAERT/IN_ABGLEICH, spiegelt die versuche-Erhoehung des Beanspruchs-Statements, da das Repository das Aggregat aus dem Vorzustand aufbaut), `fehlgeschlagenErklaeren()` (von IN_ABGLEICH) sowie `bestaetigen`/`ungeklaertErklaeren` zusaetzlich von IN_ABGLEICH (ohne versuche++) — verifiziert durch Unit-Tests in `ZustellungsZustandsmaschineTest` (neue und illegale Uebergaenge, versuche-Zaehlung)
- [x] 2.2 Backoff-Berechnung als kleine Klasse (min(basis*2^(versuche-1), cap), Jitter bis 20 %, `Random` injizierbar) — verifiziert durch Unit-Test: Exponent, Cap und Jitter-Grenzen mit fixem Random

## 3. Persistenz (control)

- [x] 3.1 `ZustellungRepository` um das atomare Beanspruchen erweitern (CTE-Statement aus design.md D2, inkl. Join auf `auftrag`, eigene kurze Transaktion mit sofortigem Commit) — verifiziert durch die ITs in Gruppe 5
- [x] 3.2 `ZustellungRepository.verbuchen` um den erwarteten Ausgangsstatus parametrisieren (IN_ZUSTELLUNG fuer Erstzustellung, IN_ABGLEICH fuer Abgleich) und `AnnahmeController`/`Zustellungssteuerung` unverändert weiterverwenden — verifiziert durch bestehende Unit-Tests (`ZustellungssteuerungTest`) und `mvn -pl raumschiffwerft-service test`

## 4. Abgleich-Steuerung und -Ports

- [x] 4.1 Port `Abgleichsport.statusAbfragen` in control anlegen und in boundary `CamelAbgleichsport` mit Route `direct:statusAbfragen` (choice nach `zielsystemtyp` auf `ImperiumZielsystem`/`RebellionZielsystem`) implementieren — verifiziert durch Unit-Test des Ports (gemockter ProducerTemplate analog `CamelZustellportTest`)
- [x] 4.2 `Abgleichssteuerung` in control: beanspruchen → Statusabfrage → ABGESCHLOSSEN=BESTAETIGT mit Referenz, IN_BEARBEITUNG/Fehler=UNGEKLAERT mit Backoff, UNBEKANNT=Neuversand ueber `Zustellport` (direkt in der Abgleichssteuerung, nicht ueber eine geteilte Methode mit der Erstzustellung, weil die max-versuche/FEHLGESCHLAGEN-Semantik nur im Abgleich gilt); max-versuche → FEHLGESCHLAGEN mit Fehler-Log; Fehler je Zeile bricht die Iteration nicht — verifiziert durch Unit-Tests mit gemocktem `Abgleichsport`/`Zustellport` (alle Mappings, max-versuche, Fehlerfortsetzung)
- [x] 4.3 `AbgleichRoute` (boundary): `from("timer:abgleich?period=...")` (Periode aus `abgleich.timer-period-millis`) → `Abgleichssteuerung.abgleichen()` — verifiziert durch Architekturtests (`mvn -pl raumschiffwerft-service test`: Camel bleibt in boundary, BCE/Layering unveraendert)

## 5. Integrationstests (Failsafe, Postgres + WireMock)

- [x] 5.1 IT-Geruest `AbgleichIT`: WireMock-Szenarien vor jedem Test via `POST /__admin/scenarios/reset` zuruecksetzen und die drei Tabellen aufraeumen (Zeilen aus frueheren Laeufen, sonst vergreift sich der Claim-Batch an Alt-Zeilen), eigene Auftrags-UUIDs je Test — verifiziert durch laufenden Test gegen die devenv-Dienste
- [x] 5.2 Fehlerfall bis BESTAETIGT: Jar-Jar-Stub laesst die Erstzustellung scheitern (202/UNGEKLAERT), danach Status-Stub ABGESCHLOSSEN; mit Awaitility warten, bis GET den Status BESTAETIGT mit externer Referenz zeigt — verifiziert durch `mvn -pl raumschiffwerft-service verify` (Failsafe); zusaetzlich Timeout-Fall (Langsam-Stub ueberschreitet das 2-s-Timeout des Imperium-Clients) bis BESTAETIGT
- [x] 5.3 Abgelaufene Lease in IN_ZUSTELLUNG (simulierter Absturz vor dem externen Aufruf): auftrag- und zustellung-Zeile direkt per SQL mit abgelaufener Lease anlegen, Status-Stub ABGESCHLOSSEN — mit Awaitility pruefen, dass der Timer die Zeile auf IN_ABGLEICH setzt und bis BESTAETIGT fuehrt (versuche 2 belegen zwei Beanspruchungen)
- [x] 5.4 UNBEKANNT loest Neuversand aus: Status-Stub UNBEKANNT (404 auf der festen Beschaffungs-Id), anschliessend Erfolg-Stub — mit Awaitility bis BESTAETIGT warten und dabei mindestens einen Neuversand (versuche >= 2) beobachten
- [x] 5.5 max-versuche: mit `MaxVersucheZweiProfile` (abgleich.max-versuche=2, eigener IT `AbgleichMaxVersucheIT`) und durchgehend scheiterndem Stub — mit Awaitility bis FEHLGESCHLAGEN warten, Fehler-Log (sichtbar in der Testausgabe) und unveraenderte Zeilenzahl pruefen

## 6. Gesamtsicherung

- [x] 6.1 `mvn verify` im Wurzelverzeichnis (devenv-Shell): Unit-, Architektur- und Integrationstests aller vier Module, keine Abschwaechung der ArchUnit-Regeln — verifiziert durch gruenen Build (53 Unit-/Arch-Tests, 14 ITs)
- [x] 6.2 Specs syncen und Change archivieren (`openspec`-Workflow), Haupt-Spec `zustellungssteuerung` erhaelt die modifizierten und neuen Anforderungen — verifiziert durch `openspec validate` und `openspec status`
