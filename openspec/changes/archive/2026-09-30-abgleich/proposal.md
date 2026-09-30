## Why

Zustellungen können dauerhaft offen bleiben: Adapterfehler verbuchen UNGEKLAERT, und ein Absturz zwischen Annahme-Commit und Verbuchung lässt Zeilen in IN_ZUSTELLUNG mit abgelaufener Lease zurück. Bislang nimmt nichts diese Zustellungen wieder auf — der Auftrag bleibt endlos bei 202. Der Abgleich schließt diese Lücke: Eine Timer-Route beansprucht fällige Zustellungen atomar, gleicht vorab den Status beim Zielsystem ab und stellt erst bei UNBEKANNT neu zu.

## What Changes

- **Neue Timer-Route** (Camel `timer`, Service-boundary): beansprucht fällige Zustellungen atomar per `UPDATE zustellung ... WHERE (auftrags_id, zielsystem) IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING`. Fällig sind Zeilen mit Status UNGEKLAERT und `naechster_versuch_um <= now()` sowie Zeilen mit Status IN_ZUSTELLUNG oder IN_ABGLEICH und abgelaufener Lease (`lease_bis < now()`). Das Beanspruchen setzt IN_ABGLEICH, eine neue Lease, `versuche + 1` und die eigene `instanz`; der kanonische Auftrag wird per Join aus `auftrag` gelesen. SKIP LOCKED verhindert doppelte Bearbeitung durch konkurrierende Instanzen.
- **Abgleich vor Neuversand** (Nie blind neu senden): Für jede beanspruchte Zustellung wird immer zuerst der Status beim gespeicherten Zielsystem abgefragt. ABGESCHLOSSEN → Verbuchung BESTAETIGT mit externer Referenz; IN_BEARBEITUNG oder Abfragefehler → Verbuchung UNGEKLAERT mit Backoff-Zeitpunkt; UNBEKANNT → erneute Zustellung über `direct:zustellen` mit anschließender Verbuchung wie bei der Erstanname (BESTAETIGT oder UNGEKLAERT).
- **Backoff und Endgültigkeit**: Backoff `basis * 2^(versuche - 1)`, gedeckelt (Cap), plus bis zu 20 % Jitter. Nach Erreichen der konfigurierbaren `max-versuche` wird die Zustellung FEHLGESCHLAGEN verbucht und ein Fehler-Log geschrieben. Alle Werte sind konfigurierbar; im Test-Profil gelten kurze Intervalle und kurze Lease.
- **Zustandsmaschine erweitert**: IN_ABGLEICH und FEHLGESCHLAGEN — bisher im Schema reserviert, aber unerreichbar — werden erreichbare Zustände mit definierten Übergängen im Aggregat `Kaufauftrag`/`Zustellung`.
- **Tests**: ITs mit Awaitility für Fehler- und Timeout-Fälle bis BESTAETIGT und für eine abgelaufene Lease in IN_ZUSTELLUNG (simulierter Absturz vor dem externen Aufruf); WireMock-Szenarien werden vor jedem Test zurückgesetzt.

Keine Änderungen am REST-Vertrag (`auftragsannahme`), am Schema der drei Tabellen (`auftragsspeicherung`) oder an Modell/Adaptern: Der bestehende Port `Zielsystem.statusAbfragen` wird genutzt, kein Adapter wird angefasst.

## Capabilities

### New Capabilities

<!-- Keine neue Capability — der Abgleich ist die Fortsetzung des Zustellungslebenszyklus. -->

### Modified Capabilities

- `zustellungssteuerung`: Die Zustandsmaschine bekommt erreichbare Übergänge für IN_ABGLEICH und FEHLGESCHLAGEN (Anforderung „Zustandsmaschine im Aggregat Kaufauftrag" wird geändert). Neu: Anforderungen für das atomare Beanspruchen fälliger Zustellungen durch die Timer-Route, für den Abgleich vor jedem Neuversand (Statusabfrage zuerst) sowie für Backoff mit Jitter und die FEHLGESCHLAGEN-Endgültigkeit.

## Impact

- **Code (nur `raumschiffwerft-service`)**:
  - `entity`: `Zustellungsstatus`/`Zustellung` — neue Übergänge IN_ZUSTELLUNG → IN_ABGLEICH, IN_ABGLEICH → BESTAETIGT/UNGEKLAERT/FEHLGESCHLAGEN, UNGEKLAERT → IN_ABGLEICH (Beanspruchung).
  - `control`: neue Abgleichssteuerung (Statusabfrage, Backoff/Jitter, max-versuche, Neuversand-Entscheidung), `ZustellungRepository` um die atomare Beanspruchs-Query (`UPDATE ... RETURNING`) und ein Verbuchen mit erwartetem Ausgangsstatus IN_ABGLEICH erweitert; `Zustellport` bzw. ein Port für die Statusabfrage (`Zielsystem.statusAbfragen` existiert im Modell bereits).
  - `boundary`: neue Timer-Route, Camel-Port für die Statusabfrage (analog `CamelZustellport`).
- **Abhängigkeiten (Service-POM)**: `camel-quarkus-timer` (laufende Route), Awaitility (test, für die ITs).
- **Konfiguration**: neue Properties für Timer-Intervall, Backoff-Basis/Cap/Jitter-Anteil, max-versuche, Lease; `%test`-Profil mit kurzen Intervallen.
- **Nicht betroffen**: `raumschiffwerft-model`, beide Adapter (Port `statusAbfragen` wird nur genutzt), Flyway-Schema (alle Spalten existieren bereits, keine Migration), WireMock-Stubs (Status-Szenarien existieren bereits: abgeschlossen, in-bearbeitung/in-arbeit, unbekannt), REST-Vertrag, devenv-Dienste.
- **Betrieb**: Ohne den Abgleich blieben fehlgeschlagene Zustellungen für immer offen; ab diesem Change konvergiert jede Zustellung gegen BESTAETIGT oder FEHLGESCHLAGEN.
