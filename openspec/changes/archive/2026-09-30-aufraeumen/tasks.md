## 1. Schema und Konfiguration

- [x] 1.1 Flyway-Migration `V2__aufraeum_indizes.sql` anlegen (partielle Indizes auf journal_outbox/gesendet und zustellung/nicht-bestaetigt, Index auf auftrag.angenommen_am) — verifiziert durch ITs gegen die devenv-Postgres (Migration laeuft beim Start) und `\d`-Check
- [x] 1.2 Konfiguration in `application.properties`: `durchlauferhitzer.aufraeumen.aufbewahrung` (Default `7d`), `durchlauferhitzer.aufraeumen.timer-period-millis` (Default 600000), `durchlauferhitzer.aufraeumen.batch` (Default 1000) — verifiziert durch Start des Service
- [x] 1.3 `AufraeumenTestProfile` (Aufbewahrung 1 s, Timer 500 ms) als eigenes QuarkusTest-Profil — verifiziert durch schnelles Konvergieren der AufraeumIT

## 2. Aufbewahrungsregel und Steuerung (control)

- [x] 2.1 `Aufbewahrungsregel` in `control.aufraeumen`: Stichtag-Berechnung aus der Frist und `loeschbar(...)` (alle BESTAETIGT und aelter als Frist; FEHLGESCHLAGEN/offene Zustellungen/leere Mengen lehnen ab) — verifiziert durch Unit-Tests (alle Faelle inkl. FEHLGESCHLAGEN)
- [x] 2.2 Port `Aufraeumung` in `control.aufraeumen` und `AufraeumSteuerung`: sperren → Journal-Batch → Kandidaten → Regelfilter → Loeschung → entsperren (finally); Sperre belegt bricht sofort ab — verifiziert durch Unit-Tests mit gemocktem Port (freie/belegte Sperre, Regelfilter, Fehlerfall mit Entsperren)

## 3. SQL-Repository und Timer-Route (boundary)

- [x] 3.1 `AufraeumRepository` in `boundary.aufraeumen`: Advisory-Lock (`pg_try_advisory_lock` auf gehaltener Connection, Freigabe am Ende), Journal-Batch-Delete, Kandidatensuche mit NOT EXISTS-Prefilter (kein Verhungern), Loeschung zustellung→auftrag je Batch in einer Transaktion — verifiziert durch die AufraeumIT und `mvn -pl raumschiffwerft-service test` (BCE/Architekturtests)
- [x] 3.2 `AufraeumRoute` in `boundary.aufraeumen`: `from("timer:aufraeumen?period=...")` → `AufraeumSteuerung.aufraeumen()` — verifiziert durch die AufraeumIT (Timer triggert den Durchlauf)

## 4. Integrationstest

- [x] 4.1 `AufraeumIT` mit `AufraeumenTestProfile`: erfolgreiche Annahme → Journal-Zeile nach Versand geloescht und auftrag/zustellung nach Ablauf der 1-s-Frist geloescht (Awaitility); per SQL angelegter FEHLGESCHLAGEN-Auftrag mit altem angenommen_am bleibt unberuehrt; die ungesendete Journal-Zeile bleibt durch die SQL-Bedingung gesichert (im IT nicht separat assertierbar, weil der Relay sie ohnehin asynchron versendet) — verifiziert durch `mvn -pl raumschiffwerft-service verify` (Failsafe)

## 5. Gesamtsicherung

- [x] 5.1 `mvn verify` im Wurzelverzeichnis (devenv-Shell): Unit-, Architektur- und Integrationstests aller vier Module — verifiziert durch gruenen Build
- [x] 5.2 Specs syncen und Change archivieren (`openspec`-Workflow); Haupt-Specs `aufraeumen` (neu), `zustellungssteuerung` (modifiziert) und `auftragsspeicherung` (neue Anforderung) aktualisieren — verifiziert durch `openspec validate` und `openspec status`
