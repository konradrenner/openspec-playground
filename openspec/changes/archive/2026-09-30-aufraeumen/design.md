## Context

Alle drei Tabellen wachsen unbeschraenkt: `journal_outbox` haelt jede Zeile nach dem Versand, Auftraege bleiben ewig liegen. Der Journal-Relay (Change journal-relay) markiert Zeilen nur mit gesendet_am. Das Loeschungsverbot steht in der Haupt-Spec `zustellungssteuerung` („Zustellungs- und Auftragszeilen DUERFEN NIE geloescht werden") und wird in diesem Change zur regelgebundenen Loeschung weiterentwickelt. Die AnnahmeId ist der Idempotency-Key — die Aufbewahrungsfrist bestimmt damit, wie lange Idempotenz gilt. Siehe proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- Periodisches Aufraeumen durch hoechstens eine Instanz gleichzeitig (pg_try_advisory_lock), batchweise (1000), regelkonform.
- Gesendete Journal-Zeilen und abgeschlossene, abgelaufene Auftraege (inkl. Zustellungen) loeschen; FEHLGESCHLAGEN unantastbar.
- Indexgestuetzte Kandidatensuche (Flyway V2), Aufbewahrungsregel in control, SQL in boundary.

**Non-Goals:**

- Kein DELETE-Endpoint, kein manuelles Aufraeumen per REST.
- Kein Archivieren/Exportieren vor der Loeschung (das Journal ist bereits in OpenSearch repliziert).
- Keine Loesch-Statistiken/Metriken.
- Kein Upgrade der OpenSearch-Dokumente (bleiben nach Loeschung der DB-Zeilen erhalten).

## Decisions

### D1: Struktur — Aufbewahrungsregel in control, SQL in boundary

- `control.aufraeumen`: `Aufbewahrungsregel` (Stichtag aus der Frist; `loeschbar(zustaende, angenommenAm, jetzt)` — alle BESTAETIGT und aelter als die Frist; FEHLGESCHLAGEN ist damit nie loeschbar) und `AufraeumSteuerung` (Durchlauf-Orchestrierung). Beide kennen kein SQL.
- `boundary.aufraeumen`: `AufraeumRepository` (SQL: Advisory-Lock, Batches, Kandidatensuche, Loeschungen) und `AufraeumRoute` (Timer). Das Repository implementiert den Port `Aufraeumung`, der in `control.aufraeumen` liegt (Muster Zustellport/Abgleichsport) — control haengt damit nicht an boundary.

### D2: Advisory-Lock ueber den gesamten Durchlauf

- `pg_try_advisory_lock(0x5241554D)` (Konstante, „RAUM" in Hex) am Durchlaufbeginn auf einer eigenen Connection; rueckt sie false, bricht die Steuerung sofort ab. Die Connection bleibt bis `pg_advisory_unlock` + Schliessen offen und traegt alle SQL des Durchlaufs — die Sperre ist sessiongebunden, das Freigeben bei Verbindungsabbau ist nur Notfall-Netz.
- `AufraeumRepository` haelt die gesperrte Verbindung fuer die Dauer des Durchlaufs als Feld (sperren/entsperren Rahmensemantik); die Steuerung ruft entsperren im finally.

### D3: SQL — Batches, Kandidatensuche ohne Verhung

- Journal-Batch: `DELETE FROM journal_outbox WHERE id IN (SELECT id FROM journal_outbox WHERE gesendet_am IS NOT NULL ORDER BY id LIMIT ?)` — gesendete Zeilen werden ohne Altersfilter geloescht (Interpretation der Beschreibung: gesendet = im OpenSearch repliziert = portable Frist entfaellt; die Aufbewahrungsfrist gilt ausdruecklich fuer Auftraege).
- Kandidatensuche mit NOT EXISTS-Prefilter: `SELECT a.auftrags_id, a.angenommen_am, array_agg(z.status) FROM auftrag a JOIN zustellung z ... WHERE a.angenommen_am < ? AND NOT EXISTS (nicht-bestaetigte Zustellung) GROUP BY ... LIMIT ?`. Der Prefilter ist essentiell: ohne ihn wuerde ein LIMIT-Batch mit unloeschbaren Kandidaten (offene Zustellungen) gefuellt und aeltere loeschbare Auftraege dahinter wuerden verhungern. Der Java-Regel-Check bleibt als autoritative Endkontrolle und ist unit-getestet.
- Loeschung: je Kandidaten-Batch `DELETE FROM zustellung WHERE auftrags_id IN (...)` und danach `DELETE FROM auftrag WHERE auftrags_id IN (...)` (FK-Reihenfolge; journal_outbox hat keinen FK und wird unabhaengig im Journal-Batch geloescht). Die Kandidaten-Loeschung ist eine Transaktion auf der Lock-Connection.

### D4: Aufbewahrungsregel und Konfiguration

- `durchlauferhitzer.aufraeumen.aufbewahrung` (MicroProfile-`Duration`, Default `7d`) = Frist und Idempotenzfenster; `durchlauferhitzer.aufraeumen.timer-period-millis` (Default 600000 = 10 Minuten); `durchlauferhifter.aufraeumen.batch` (Default 1000).
- Test-Profil: eigene `AufraeumenTestProfile`-IT mit Aufbewahrung 1 s und Timer 500 ms — bewusst NICHT im globalen %test-Profil, damit kein anderer IT zufaellig Zeilen verliert, bevor er sie prueft.

### D5: Flyway-Migration `V2__aufraeum_indizes.sql`

```sql
CREATE INDEX idx_journal_outbox_gesendet ON journal_outbox (id) WHERE gesendet_am IS NOT NULL;
CREATE INDEX idx_zustellung_nicht_bestaetigt ON zustellung (auftrags_id) WHERE status <> 'BESTAETIGT';
CREATE INDEX idx_auftrag_angenommen_am ON auftrag (angenommen_am);
```

Rein additiv (nur Indizes); bestaehende Tabellen unberuehrt.

### D6: Teststrategie

- Unit-Tests (kein `@QuarkusTest`): `Aufbewahrungsregel` (Stichtag; loeschbar bei alle-BESTAETIGT+alt; nicht loeschbar bei frisch, offener oder fehlgeschlagener Zustellung, leeren Zustellungen), `AufraeumSteuerung` mit gemocktem Port (Sperre frei → Journal-Batch, Kandidaten, Regelfilter, Loeschung; Sperre belegt → kein SQL; Regel lehnt ab → keine Loeschung; Fehler → Entsperren im finally).
- IT `AufraeumIT` (`@TestProfile(AufraeumenTestProfile)`, Aufbewahrung 1 s): erfolgreiche Annahme → Journal-Zeile nach Versand geloescht, auftrag/zustellung nach Ablauf geloescht (Awaitility); per SQL angelegter FEHLGESCHLAGEN-Auftrag mit altem angenommen_am bleibt; ungesendete Journal-Zeile bleibt (Kafka-Stopp simulieren wir nicht — die Zeile wird vor dem Relay-Versand geprueft).
- Bestehende ITs unberuehrt: Default-Frist 7 d loescht nichts, was ITs anlegen.

## Risks / Trade-offs

- [Loeschung ist final — kein Weg zurueck] → Regel bewusst konservativ (alle BESTAETIGT + Frist), FEHLGESCHLAGEN bleibt als Beweis; das Journal ist in OpenSearch ohnehin repliziert.
- [Gesendete Journal-Zeilen werden sofort geloescht, nicht erst nach Fristablauf] → bewusste Interpretation der Beschreibung (Frist gilt fuer Auftraege); falls eine Frist gewuenscht ist, ist das ein Ein-Zeilen-Patch in der Abfrage.
- [Stateful Repository (Lock-Connection als Feld)] → bewusst, weil Advisory-Locks sessiongebunden sind; die Steuerung erzwingt entsperren im finally, parallele Durchlaeufe desselben Pods sind durch die Sperre ausgeschlossen.
- [Batch-Loeschung mit 1000er-IN-Listen] → unproblematisch in Postgres; bei Bedarf ist batch klein genug, um Transaktionen kurz zu halten.
- [Alte Testzeilen in devenv verschwinden beim ersten Durchlauf] → gewuenscht; keine produktiven Daten in der Entwicklungsdatenbank.

## Migration Plan

Additiv: Flyway V2 legt Indizes an; der Timer beginnt nach Ablauf des Intervalls. Rollback = Deploy des alten Stands; bereits geloeschte Zeilen bleiben geloescht (Regelkonform, KEIN Datenverlust im Sinne des Vertrags — nur regelkonform Entferntes).

## Open Questions

Keine — die Interpretation „gesendete Journal-Zeilen ohne Altersfilter" ist als Risiko/Trade-off dokumentiert und als Ein-Zeiler anpassbar, ohne den Spec-Vertrag zu beruehren (die Spec formuliert es bewusst unabhaengig von einem Alter: gesendet_am gesetzt).
