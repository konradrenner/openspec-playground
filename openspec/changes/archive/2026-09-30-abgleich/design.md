## Context

Der Change `annahme-und-zustellung` (archiviert) hat Erstannahme, Erstzustellung und Verbuchung etabliert: `Zustellungssteuerung` (control) ruft nach dem Commit synchron `direct:zustellen` (boundary, `ZustellRoute` mit choice nach `Zielsystemtyp`) auf und verbucht per konditionalem Update mit erwartetem Ausgangsstatus IN_ZUSTELLUNG. Die Zustandsmaschine (`Zustellung`/`Zustellungsstatus`, entity) erlaubt bisher nur IN_ZUSTELLUNG → BESTAETIGT/UNGEKLAERT; IN_ABGLEICH und FEHLGESCHLAGEN sind reserviert. Das Modell bietet mit `Zielsystem.statusAbfragen(AuftragsId) -> Verarbeitungsstatus` bereits den Port; WireMock-Stubs fuer Statusabfragen existieren (imperium: abgeschlossen/in-bearbeitung, rebellion: erledigt/in-arbeit/unbekannt, teils szenariengesteuert). Siehe proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- Faellige Zustellungen (UNGEKLAERT faellig, abgelaufene Lease) werden periodisch, atomar und instanzensicher wieder aufgegriffen.
- Vor jedem Neuversand steht die Statusabfrage beim Zielsystem; nur UNBEKANNT fuehrt zu einem Neuversand ueber die bestehende Zustell-Route.
- Backoff mit exponentiellem Wachstum, Cap und Jitter; konfigurierbares Endgültig-Werden (FEHLGESCHLAGEN).
- Testbar mit kurzen Intervallen (Awaitility-ITs), ohne dass Produktivwerte angepasst werden muessen.

**Non-Goals:**

- Keine Aenderung an Modell, Adaptern oder REST-Vertrag; kein neues Flyway-Migration (alle Spalten existieren).
- Kein Kafka-Versand des Outbox (eigener Change).
- Keine Lease-Uebernahme-Feinheiten jenseits von SKIP LOCKED (kein Zombie-Check, kein Abbruch laufender Fremd-Instanzen).
- Kein Manuell-Trigger-Endpoint und kein Admin-API fuer den Abgleich.

## Decisions

### D1: Timer-Route in boundary, Abgleichslogik in control

- Neue Camel-Route `AbgleichRoute` (boundary): `from("timer:abgleich?period={{abgleich.timer-period-millis}}")` ruft eine Methode der neuen `Abgleichssteuerung` (control) auf. Neuheit im POM: `camel-quarkus-timer`.
- Die `Abgleichssteuerung` kennt weder Camel noch Adapter; sie haengt an Ports in control (wie `Zustellport`), deren Implementierungen in boundary liegen. Damit bleibt die ArchUnit-Regel „Camel nur in boundary" unberuehrt.
- Alternative: Quarkus `@Scheduled` — verworfen, weil der Fachkontext (Route, periodisches Wiederaufgreifen im Camel-getriebenen Service) mit der Timer-Komponente konsistent ist und die Route dieselbe Form wie `ZustellRoute` hat.

### D2: Atomares Beanspruchen als einzelnes Statement mit CTE

Ein Statement (plain JDBC, eigene kurze Transaktion, Commit sofort nach dem RETURNING, damit die neue Lease fuer andere Instanzen sichtbar ist, bevor verarbeitet wird):

```sql
WITH frei AS (
  SELECT auftrags_id, zielsystem FROM zustellung
  WHERE (status = 'UNGEKLAERT' AND naechster_versuch_um <= now())
     OR (status IN ('IN_ZUSTELLUNG', 'IN_ABGLEICH') AND lease_bis < now())
  ORDER BY auftrags_id, zielsystem
  LIMIT ? FOR UPDATE SKIP LOCKED
), beansprucht AS (
  UPDATE zustellung z SET status = 'IN_ABGLEICH',
        lease_bis = ?, versuche = z.versuche + 1, instanz = ?, aktualisiert_am = now()
  WHERE (z.auftrags_id, z.zielsystem) IN (SELECT auftrags_id, zielsystem FROM frei)
  RETURNING z.auftrags_id, z.zielsystem, z.versuche
)
SELECT b.auftrags_id, b.zielsystem, b.versuche, a.kaufauftrag
FROM beansprucht b JOIN auftrag a ON a.auftrags_id = b.auftrags_id
```

- Der Join auf `auftrag` liefert den kanonischen Auftrag gleich mit (RETURNING allein koennte nur Spalten von `zustellung` liefern). Das frei-CTE selektiert zusaetzlich Ausgangsstatus und -versuche der Zeile, damit das Repository das Aggregat im Vorzustand aufbauen kann.
- `LIMIT` (Batchgroesse, `abgleich.batch`, Default 20) haelt eine Iteration kurz; SKIP LOCKED macht konkurrierende Instanzen sicher.
- Alternative: SELECT ... FOR UPDATE SKIP LOCKED und danach einzelne UPDATEs — verworfen: zwei Statements, mehr Zeitfenster, mehr Roundtrips ohne Gewinn.
- Umsetzungskorrektur (Apply-Phase): Das BEANSPRUCHEN-Statement liefert Ausgangsstatus/-versuche aus dem frei-CTE mit; das Repository baut die Aggregat-Zeile im Vorzustand und ruft `beanspruchen(...)` auf, das denselben Uebergang samt versuche-Erhoehung spiegelt. So ist `beanspruchen` produktiver Code, und Entity und Datenbank zaehlen identisch.

### D3: Zustandsmaschine und versuche-Zaehlung

- `Zustellungsstatus` erhaelt pro Status die Menge erlaubter Folgezustaende; `Zustellung` bekommt:
  - `beanspruchen(leaseBis, instanz)`: erlaubt von IN_ZUSTELLUNG, UNGEKLAERT, IN_ABGLEICH; setzt status IN_ABGLEICH, neue Lease, instanz und erhoehen versuche - dasselbe wie das Beanspruchs-Statement (das Repository baut das Aggregat aus dem Vorzustand, siehe D2-Korrektur).
  - `bestaetigen(externeReferenz)` / `ungeklaertErklaeren(naechsterVersuchUm)`: kuenftig erlaubt von IN_ZUSTELLUNG (Erstzustellung, wie bisher inkl. versuche++) und von IN_ABGLEICH (Abgleich, ohne weiteres versuche++, weil die Erhoehung Teil des Beanspruchens ist).
  - `fehlgeschlagenErklaeren()`: erlaubt von IN_ABGLEICH.
- Illegale Uebergaenge werfen weiterhin `IllegalStateException`; vor dem SQL entscheidet das Aggregat, das konditionale Update (erwarteter Ausgangsstatus) schuetzt zusaetzlich.

### D4: Ports und Abgleichsablauf (control)

- Neuer Port `Abgleichsport.statusAbfragen(AuftragsId, Zielsystemtyp)` in control; Implementierung `CamelAbgleichsport` in boundary mit Route `direct:statusAbfragen` (choice nach `zielsystemtyp`, analog `ZustellRoute`).
- `Abgleichssteuerung.abgleichen()` verarbeitet jede beanspruchte Zeile einzeln:
  1. `Abgleichsport.statusAbfragen(...)` — immer zuerst.
  2. ABGESCHLOSSEN → `bestaetigen(externeReferenz)` (ohne Referenz bleibt die Zustellung offen und konvergiert gegen FEHLGESCHLAGEN); IN_BEARBEITUNG oder `ZustellungUngeklaert` → `ungeklaertErklaeren(backoff)`; UNBEKANNT → Neuversand ueber den bestehenden `Zustellport.zustellen`, direkt in der Abgleichssteuerung (keine geteilte Methode mit der Erstzustellung, weil die max-versuche/FEHLGESCHLAGEN-Semantik nur im Abgleich gilt - KISS vor Code-Deduplizierung).
  3. Endgueltigkeit: ist das Ergebnis nicht BESTAETIGT und `versuche >= max-versuche`, wird `fehlgeschlagenErklaeren()` aufgerufen und ein Fehler-Log geschrieben (deterministische, einfache Regel; IN_BEARBEITUNG bei erschöpften Versuchen endet ebenfalls endgueltig).
  4. Ein Fehler bei einer Zeile bricht die Iteration nicht ab (loggen, weiter mit der naechsten Zeile).
- Der Adapter verbucht weiterhin nichts; er wird nur ueber `statusAbfragen`/`zustellen` genutzt.

### D5: Backoff mit Cap und Jitter

- `naechster_versuch_um = now + min(basis * 2^(versuche - 1), cap) * (1 + j)`, `j` gleichverteilt in [0, 0.2]; `versuche` ist der Wert nach dem Beanspruchen.
- Konfiguration:
  - `zustellung.wiederholung-sekunden` (existiert, Default 60) = Backoff-Basis.
  - neu: `abgleich.backoff-max-sekunden` (Default 3600), `abgleich.jitter-anteil` (Default 0.2), `abgleich.max-versuche` (Default 5), `abgleich.timer-period-millis` (Default 10000), `abgleich.batch` (Default 20).
  - Lease: `zustellung.lease-minuten` wird zu `zustellung.lease-sekunden` (Default 600), damit das Test-Profil eine Lease unter einer Minute fahren kann. Rein interne Konfigurationsaenderung (application.properties), kein Spec-Bruch.
  - `%test`-Profil: Timer-Periode ~500 ms, Lease ~2 s, Backoff-Basis ~1 s, max-versuche 2 — damit die ITs mit Awaitility schnell konvergieren.

### D6: Verbuchen im Abgleich

- `ZustellungRepository.verbuchen(...)` wird um den erwarteten Ausgangsstatus parametrisiert (IN_ZUSTELLUNG fuer Erstzustellung, IN_ABGLEICH fuer Abgleich): weiterhin GENAU EIN Update per Primaerschluessel, Rueckgabe 0 = unerwarteter Ausgangsstatus, nichts geschrieben.

### D7: Teststrategie

- Unit-Tests (Surefire, kein `@QuarkusTest`): Zustandsmaschine (alle neuen Uebergaenge, illegale Uebergaenge, versuche-Zaehlung beim Beanspruchen), Backoff-Berechnung (Exponent, Cap, Jitter-Grenzen mit fixem Random), Abgleichssteuerung mit gemocktem `Abgleichsport`/`Zustellport` (Mapping ABGESCHLOSSEN/IN_BEARBEITUNG/UNBEKANNT/Fehler, max-versuche → FEHLGESCHLAGEN mit Fehler-Log).
- ITs (Failsafe, `@QuarkusTest`, Postgres + WireMock aus devenv), Neuheit `org.awaitility:awaitility` (test):
  - Vor jedem Test: WireMock-Szenarien via `POST /__admin/scenarios/reset` zuruecksetzen (Muster aus den Adapter-ITs).
  - Fehler-/Timeout-Fall: Jar-Jar-Stub schlaegt bei der Erstzustellung fehl (202, UNGEKLAERT), danach meldet der Status-Stub ABGESCHLOSSEN — Awaitility wartet, bis `GET /api/v1/kaufauftraege/{id}` BESTAETIGT mit Referenz zeigt.
  - Abgelaufene Lease in IN_ZUSTELLUNG (simulierter Absturz vor dem externen Aufruf): auftrag- und zustellung-Zeile direkt per SQL mit abgelaufener Lease anlegen, Status-Stub ABGESCHLOSSEN — Timer nimmt die Zeile auf, bis BESTAETIGT.
  - UNBEKANNT: Status-Stub meldet UNBEKANNT, anschliessender Neuversand trifft den Erfolg-Stub — bis BESTAETIGT.
  - max-versuche: mit `%test.abgleich.max-versuche=2` und durchgehend scheiternden Stubs — bis FEHLGESCHLAGEN, Zeile bleibt.
- Architekturtests laufen unverändert weiter; die neuen Klassen liegen in den bekannten Layern (Route und Ports in boundary, Steuerung und Repository in control).

## Risks / Trade-offs

- [Szenariobasierte WireMock-Stubs beeinflussen sich zwischen Tests] → Reset der Szenarien vor jedem Test, eigene Auftrags-UUIDs je Test.
- [Bei IN_BEARBEITUNG mit erschöpften Versuchen endet die Zustellung FEHLGESCHLAGEN, obwohl das Zielsystem spaeter abschliessen koennte] → bewusste KISS-Entscheidung (deterministische Regel, Endlichkeit garantiert); die Zeile bleibt fuer spaetere Betrachtung erhalten.
- [Timer-Periode und Batchgroesse bestimmen Lastspitzen auf der DB] → konfigurierbar (`abgleich.timer-period-millis`, `abgleich.batch`); partieller Index auf offene Zustaende stuetzt das Selektieren.
- [Crash zwischen Beanspruchen (Commit) und Verbuchen] → Zeile bleibt IN_ABGLEICH mit Lease und wird beim naechsten Durchlauf erneut beansprucht — genau der vorgesehene Selbstheilungspfad.
- [Annahme-ITs und Abgleich-ITs teilen sich eine Datenbank] → Abgleich-ITs nutzen eigene Auftrags-UUIDs; keine Wechselwirkung, da der Timer nur offene Zeilen greift.

## Migration Plan

Rein additiv im Code; keine Flyway-Migration. Konfiguration: `zustellung.lease-minuten` wird durch `zustellung.lease-sekunden` ersetzt (nur application.properties betroffen). Ausrollen = Deploy; der Timer beginnt sofort, offene Alt-Zeilen werden automatisch aufgenommen (der gewuenschte Effekt). Rollback = Deploy des alten Stands; offene Zeilen bleiben unberuehrt liegen, bis wieder abgeglichen wird.
