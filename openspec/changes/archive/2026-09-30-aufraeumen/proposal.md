## Why

Zeilen werden heute nie geloescht: `journal_outbox` waechst mit jedem Auftrag, und auch abgeschlossene Auftraege bleiben ewig liegen. Der Change etabliert die Aufbewahrung als betriebliche Regel: Gesendetes und vollstaendig abgeschlossenes wird nach Ablauf einer konfigurierbaren Frist batchweise geloescht, Fehlgeschlagenes bleibt als Beweis dauerhaft erhalten.

## What Changes

- **Neue Aufraeum-Route (Timer, konfigurierbares Intervall, Default 10 Minuten)**: sichert sich zu Beginn des Durchlaufs mit `pg_try_advisory_lock` ab und bricht ab, wenn ein anderer Pod bereits aueraeumt — nur eine Instanz loescht gleichzeitig. Die Sperre gilt fuer den gesamten Durchlauf und wird am Ende freigegeben.
- **Batchloeschung à 1000 Zeilen**: gesendete `journal_outbox`-Zeilen (gesendet_am gesetzt) sowie Auftraege samt ihren Zustellungszeilen, deren Zustellungen alle BESTAETIGT sind und deren Annahme aelter ist als die Aufbewahrungsfrist. Auftraege mit einer Zustellung in FEHLGESCHLAGEN werden NIE automatisch geloescht.
- **Aufbewahrungsfrist als Idempotenzfenster**: `durchlauferhitzer.aufraeumen.aufbewahrung` (Default 7 Tage, konfigurierbar) bestimmt, wie lange ein wiederholter POST mit gleichem Idempotency-Key den bestehenden Stand zurueckliefert; nach Loeschung ist derselbe Key eine Neuanname.
- **Partielle Indizes fuer die Loeschabfragen** (Flyway-Migration V2): auf `journal_outbox` fuer gesendete Zeilen und auf `zustellung` fuer nicht-bestaetigte Zeilen, damit die Kandidatensuche ohne Vollscan laeuft.
- **Struktur**: Aufbewahrungsregel in control, SQL im Repository in boundary (Port-Muster wie bisher); IT mit kurzer Aufbewahrung im Test-Profil, Unit-Tests fuer die Regel.

Keine Aenderungen am REST-Vertrag, an Annahme/Zustellung/Abgleich/Journal-Ablauf oder an Modell/Adaptern. **Bewusste Abweichung vom bisherigen Loeschungsverbot** in `zustellungssteuerung`: Loeschungen gibt es ab jetzt nur durch das Aufraeumen, gemaess der Aufbewahrungsregel — nie durch andere Komponenten.

## Capabilities

### New Capabilities

- `aufraeumen`: Fachlicher Vertrag der Aufbewahrung und Loeschung — Timer mit Advisory-Lock, Batchloeschung gesendeter Outbox-Zeilen und abgeschlossener Auftraege, Aufbewahrungsfrist als Idempotenzfenster, Unantastbarkeit von FEHLGESCHLAGEN.

### Modified Capabilities

- `zustellungssteuerung`: Die Anforderung „Zustellungszeilen mit Lease, nie Loeschungen" aendert sich — das pauschale Loeschungsverbot wird zur regelgebundenen Loeschung: nur das Aufraeumen loescht, und nur abgeschlossene, abgelaufene Auftraege samt Zustellungen.
- `auftragsspeicherung`: Neu Anforderung „Partielle Indizes fuer das Aufraeumen" — die Loeschabfragen werden durch partielle Indizes auf journal_outbox (gesendet) und zustellung (nicht bestaetigt) gestuetzt (Flyway V2).

## Impact

- **Code (nur `raumschiffwerft-service`)**: neue Aufraeum-Komponente — Aufbewahrungsregel + Steuerung in `control`, SQL-Repository (Advisory-Lock, Batch-Deletes) und Timer-Route in `boundary`; keine bestehende Klasse wird angefasst.
- **Datenbank**: neue Flyway-Migration `V2__aufraeum_indizes.sql` (nur Indizes, keine Spalten/Tabellen); Loeschungen betreffen `journal_outbox` (gesendet), `zustellung` und `auftrag` (abgeschlossen + abgelaufen).
- **Konfiguration**: `durchlauferhitzer.aufraeumen.aufbewahrung` (Default `7d`), `durchlauferhitzer.aufraeumen.timer-period` (Default 10 Minuten), `durchlauferhitzer.aufraeumen.batch` (Default 1000); Test-Profil mit kurzer Aufbewahrung und Timer-Periode.
- **Tests**: Unit-Tests fuer die Aufbewahrungsregel (Stichtag, Loeschbarkeit, FEHLGESCHLAGEN-Unantastbarkeit); IT mit kurzer Aufbewahrung: gesendete Journal-Zeile und abgeschlossener Auftrag verschwinden, FEHLGESCHLAGEN-Auftrag bleibt; bestehende ITs unberuehrt (7-Tage-Default loescht nichts Frisches).
- **Nicht betroffen**: REST-Vertrag, Zustell-/Abgleich-/Journal-Kette, Modell, Adapter, WireMock-Stubs.
- **Betrieb**: Nach dem Deploy laeuft das Aufraeumen erstmals nach Ablauf des Intervalls und arbeitet dann Bestand ab; in devenv enthaelt die Datenbank alte Testzeilen, die dann wegfallen — gewuenscht und unschaedlich.
