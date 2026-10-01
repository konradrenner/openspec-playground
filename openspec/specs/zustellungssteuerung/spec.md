# Zustellungssteuerung Specification

## Purpose

Steht für den fachlichen Ablauf von der angenommenen Bestellung bis zur verbuchten Zustellung: Auswahl des Zielsystems per Feature-Flag, genau eine Zustellung pro Auftrag nach dem Commit, Abgleich und Wiederholung faelliger Zustellungen, Verbuchung des Ergebnisses über die Zustandsmaschine.

## Requirements

### Requirement: Zielsystemwahl per OpenFeature-Flag
Das Zielsystem MUSS per OpenFeature-Flag `zielsystem` gewaehlt werden (flagd im Datei-Modus, `flags/flags.json`). Das Flag MUSS nur die Werte imperium und rebellion annehmen koennen. Fuer die Lieferplaneten 4 („Yavin 4"), 5 („Hoth") und 6 („Dantooine") MUSS rebellion gewaehlt werden. In allen anderen Faellen sowie bei einem Fehler des Flag-Providers MUSS imperium gewaehlt und eine Warnung geloggt werden. Die Flag-Auswertung MUSS genau einmal pro Auftrag und vor dem Commit der Annahme erfolgen.

#### Scenario: Flag liefert rebellion
- **WHEN** ein Auftrag mit lieferplanet 4, 5 oder 6 angenommen wird
- **THEN** waehlt die Zustellungssteuerung rebellion als Zielsystem

#### Scenario: Flag liefert imperium
- **WHEN** ein Auftrag mit einem anderen lieferplanet (z. B. 42) angenommen wird
- **THEN** waehlt die Zustellungssteuerung imperium als Zielsystem

#### Scenario: Providerfehler faellt auf imperium zurueck
- **WHEN** die Flag-Auswertung fehlschlaegt (Provider nicht verfuegbar oder ungueltige Antwort)
- **THEN** waehlt die Zustellungssteuerung imperium, loggt eine Warnung und bricht die Annahme nicht ab

#### Scenario: Genau eine Auswertung vor dem Commit
- **WHEN** ein Auftrag angenommen wird
- **THEN** wird das Flag genau einmal ausgewertet und das Ergebnis wird noch innerhalb der Annahme-Transaktion (vor dem Commit) als Zustellungszeilenmaterial verwendet

### Requirement: Zustellung nach dem Commit synchron aus dem Speicher
Nach dem Commit der Annahme MUSS die Zustellung synchron erfolgen, ohne erneuten Datenbank-Select: das kanonische Modell MUSS aus dem Speicher an die Route `direct:zustellen` uebergeben werden, die per choice nach Zielsystemtyp den entsprechenden Adapter aufruft.

#### Scenario: Zustellung laeuft ueber die Camel-Route
- **WHEN** die Annahme committet wurde
- **THEN** wird der Auftrag synchron ueber `direct:zustellen` und den fuer den Zielsystemtyp gewaehlten Adapter zugestellt

#### Scenario: Kein DB-Select nach dem Commit
- **WHEN** die Zustellung nach dem Commit laeuft
- **THEN** wird der Auftrag nicht erneut aus der Datenbank gelesen

### Requirement: Zustell-Route prueft den kanonischen Auftrag per Bean Validation
Die Zustell-Route `direct:zustellen` MUSS den kanonischen Kaufauftrag per Bean Validation prüfen, BEVOR das Zielsystem angewählt wird. Ein Verstoß DARF NICHT an das Zielsystem gesendet werden und MUSS wie jeder technische Misserfolg der Zustellung behandelt werden: gemeldet als `ZustellungUngeklaert` und verbucht als UNGEKLAERT mit `naechster_versuch_um`, sodass Backoff und Abgleich wie bei Adapterfehlern greifen.

#### Scenario: Verletzender Auftrag wird nicht zugestellt
- **WHEN** die Zustell-Route einen kanonischen Kaufauftrag erhält, der die Bean-Validation-Regeln verletzt
- **THEN** ruft die Route das Zielsystem nicht auf, und die Zustellung wird als UNGEKLAERT mit naechster_versuch_um verbucht

#### Scenario: Gueltiger Auftrag wird normal zugestellt
- **WHEN** die Zustell-Route einen regelkonformen kanonischen Kaufauftrag erhält
- **THEN** wird er unverändert an das gewählte Zielsystem zugestellt

### Requirement: Verbuchung mit genau einem erwarteten Update
Nach jedem Zustell- oder Abgleichsschritt MUSS pro Zustellungszeile GENAU EIN Update per Primaerschluessel ausgefuehrt werden, und NUR wenn die Zeile noch den erwarteten Ausgangsstatus hat: nach der Erstzustellung IN_ZUSTELLUNG (bei Erfolg auf BESTAETIGT mit externer Referenz, bei Misserfolg auf UNGEKLAERT mit naechster_versuch_um), nach der Beanspruchung durch die Abgleich-Route IN_ABGLEICH (auf BESTAETIGT, UNGEKLAERT oder FEHLGESCHLAGEN). Der Adapter MUSS selbst nichts verbuchen.

#### Scenario: Erfolg wird als BESTAETIGT verbucht
- **WHEN** der Adapter eine Zustellbestaetigung mit externer Referenz liefert
- **THEN** wird die Zustellungszeile per Primaerschluessel auf BESTAETIGT mit der externen Referenz aktualisiert

#### Scenario: Misserfolg wird als UNGEKLAERT verbucht
- **WHEN** der Adapter ZustellungUngeklaert wirft
- **THEN** wird die Zustellungszeile per Primärschluessel auf UNGEKLAERT mit naechster_versuch_um aktualisiert

#### Scenario: Abgleichsergebnis wird per Primaerschluessel verbucht
- **WHEN** der Abgleich einer IN_ABGLEICH-Zeile ein Ergebnis liefert
- **THEN** wird die Zustellungszeile per Primaerschluessel genau einmal auf das Ergebnis-Status aktualisiert

#### Scenario: Kein Update bei unerwartetem Ausgangsstatus
- **WHEN** die Zustellungszeile bei dem Update nicht mehr den erwarteten Ausgangsstatus hat
- **THEN** wird keine Statusaenderung geschrieben

### Requirement: Zustandsmaschine im Aggregat Kaufauftrag
Der Auftrag MUSS als Aggregat `Kaufauftrag` mit seinen Zustellungen modelliert sein und die Zustaende der Zustellungen verwalten. Erlaubte Uebergaenge: IN_ZUSTELLUNG → BESTAETIGT oder UNGEKLAERT (Erstzustellung), IN_ZUSTELLUNG → IN_ABGLEICH sowie UNGEKLAERT → IN_ABGLEICH (Beanspruchen durch die Abgleich-Route), IN_ABGLEICH → IN_ABGLEICH (erneute Beanspruchen nach abgelaufener Lease), IN_ABGLEICH → BESTAETIGT, UNGEKLAERT oder FEHLGESCHLAGEN (Abgleichsergebnis). Illegale Uebergaenge MUSSEN abgelehnt werden und duerfen zu keinem Schreibzugriff fuehren. Alle fuenf Zustaende sind erreichbar.

#### Scenario: Zustandsuebergang bei Erfolg
- **WHEN** die Zustellung einer IN_ZUSTELLUNG-Zeile erfolgreich ist
- **THEN** geht das Aggregat diese Zustellung in BESTAETIGT ueber

#### Scenario: Beanspruchung fuehrt in den Abgleich
- **WHEN** eine faellige Zustellung (UNGEKLAERT mit faelligem naechster_versuch_um oder abgelaufener Lease) von der Abgleich-Route beansprucht wird
- **THEN** geht das Aggregat diese Zustellung in IN_ABGLEICH ueber

#### Scenario: Abgleichsergebnis wird ueber die Zustandsmaschine verbucht
- **WHEN** eine IN_ABGLEICH-Zeile abgeschlossen, ungeklaert oder nach max-versuchen endgueltig gescheitert ist
- **THEN** geht das Aggregat diese Zustellung in BESTAETIGT, UNGEKLAERT bzw. FEHLGESCHLAGEN ueber

#### Scenario: Illegale Uebergaenge werden abgelehnt
- **WHEN** ein Uebergang angestossen wird, den die Zustandsmaschine nicht erlaubt (z. B. BESTAETIGT → UNGEKLAERT)
- **THEN** wird der Uebergang abgelehnt und es entsteht kein Schreibzugriff auf die Zeile

#### Scenario: Reservierte Zustaende sind vorhanden
- **WHEN** die Zustandsmaschine des Aggregats betrachtet wird
- **THEN** umfasst sie IN_ZUSTELLUNG, IN_ABGLEICH, UNGEKLAERT, BESTAETIGT und FEHLGESCHLAGEN, wobei alle fuenf Zustaende erreichbar sind: IN_ABGLEICH durch das Beanspruchen der Abgleich-Route, FEHLGESCHLAGEN nach max-versuchen

### Requirement: Zustellungszeilen mit Lease, nie Loeschungen
Bei der Annahme MUSS pro gewaehltem Zielsystem eine Zustellungszeile als IN_ZUSTELLUNG mit Lease (lease_bis, instanz) angelegt werden. Loeschungen von Zustellungs- und Auftragszeilen gibt es NUR durch das Aufraeumen (Faehigkeit aufraeumen) und nur gemaess der Aufbewahrungsregel: Auftraege, deren Zustellungen alle BESTAETIGT sind und deren Aufbewahrungsfrist abgelaufen ist. Jede andere Komponente DARF keine Zeilen loeschen; Auftraege mit einer Zustellung in FEHLGESCHLAGEN werden von keinem automatischen Aufraeumen geloescht.

#### Scenario: Zustellungszeile mit Lease
- **WHEN** ein Auftrag mit Zielsystem imperium angenommen wird
- **THEN** existiert danach genau eine Zustellungszeile (auftrags_id, imperium) mit status IN_ZUSTELLUNG und gesetztem lease_bis sowie instanz

#### Scenario: Keine Loeschung
- **WHEN** eine Zustellung bestaetigt oder fuer ungeklaert erklaert wurde
- **THEN** bleibt die Zeile bestehen, solange die Aufbewahrungsfrist des Auftrags nicht abgelaufen ist oder nicht alle Zustellungen BESTAETIGT sind; geloescht wird ausschliesslich durch das Aufraeumen gemaess der Aufbewahrungsregel

### Requirement: Abgleich-Route beansprucht faellige Zustellungen atomar
Eine Abgleich-Route MUSS periodisch (konfigurierbares Intervall) faellige Zustellungen beanspruchen. Das Beanspruchen MUSS atomar per `UPDATE zustellung ... WHERE (auftrags_id, zielsystem) IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING` erfolgen, sodass konkurrierende Instanzen dieselbe Zeile nie doppelt bearbeiten. Faellig sind Zeilen mit Status UNGEKLAERT und `naechster_versuch_um <= now()` sowie Zeilen mit Status IN_ZUSTELLUNG oder IN_ABGLEICH und abgelaufener Lease (`lease_bis < now()`). Das Beanspruchen MUSS den Status auf IN_ABGLEICH setzen, eine neue Lease vergeben, `versuche` um eins erhoehen und die eigene `instanz` eintragen. Der kanonische Auftrag MUSS per Join aus `auftrag` gelesen werden.

#### Scenario: Faellige UNGEKLAERT-Zeile wird beansprucht
- **WHEN** der naechste Versuchszeitpunkt einer UNGEKLAERT-Zeile erreicht ist
- **THEN** beansprucht die Abgleich-Route die Zeile: Status IN_ABGLEICH, neue Lease, versuche + 1, eigene instanz

#### Scenario: Abgelaufene Lease in IN_ZUSTELLUNG wird beansprucht
- **WHEN** eine Zustellung nach der Annahme nicht verbucht wurde (simulierter Absturz vor dem externen Aufruf) und ihre Lease abgelaufen ist
- **THEN** beansprucht die Abgleich-Route die Zeile und setzt sie auf IN_ABGLEICH mit neuer Lease und versuche + 1

#### Scenario: Konkurrierende Instanzen bearbeiten nicht dieselbe Zeile
- **WHEN** zwei Instanzen die Abgleich-Route gleichzeitig ausfuehren
- **THEN** beansprucht hoechstens eine Instanz eine bestimmte Zustellungszeile (FOR UPDATE SKIP LOCKED)

#### Scenario: Nicht faellige Zeilen bleiben unberuehrt
- **WHEN** eine Zustellung weder einen faelligen naechster_versuch_um hat noch eine abgelaufene Lease
- **THEN** wird ihre Zeile von der Abgleich-Route nicht veraendert

### Requirement: Abgleich fragt immer zuerst den Status beim Zielsystem ab
Nach dem Beanspruchen MUSS der Abgleich immer zuerst den Verarbeitungsstatus beim gespeicherten Zielsystem abfragen (`statusAbfragen`) und NIEMALS blind neu senden. Bei ABGESCHLOSSEN MUSS die Zustellung als BESTAETIGT mit der externen Referenz verbucht werden. Bei IN_BEARBEITUNG oder einem Abfragefehler (ZustellungUngeklaert) MUSS die Zustellung als UNGEKLAERT mit Backoff-Zeitpunkt verbucht werden, ohne den Abgleich abzubrechen. Bei UNBEKANNT MUSS der Auftrag erneut ueber die Zustell-Route `direct:zustellen` zugestellt und das Ergebnis wie bei der Erstzustellung verbucht werden (BESTAETIGT mit externer Referenz oder UNGEKLAERT mit naechster_versuch_um).

#### Scenario: Abgeschlossen wird bestaetigt
- **WHEN** das Zielsystem fuer eine beanspruchte Zustellung den Status ABGESCHLOSSEN meldet
- **THEN** wird die Zustellung als BESTAETIGT mit der gemeldeten externen Referenz verbucht

#### Scenario: In Bearbeitung bleibt ungeklaert
- **WHEN** das Zielsystem den Status IN_BEARBEITUNG meldet
- **THEN** wird die Zustellung als UNGEKLAERT mit einem Backoff-Zeitpunkt verbucht

#### Scenario: Abfragefehler bricht den Abgleich nicht ab
- **WHEN** die Statusabfrage scheitert (Timeout, Fehlerantwort)
- **THEN** wird die Zustellung als UNGEKLAERT mit Backoff-Zeitpunkt verbucht und der Abgleich laeuft mit den uebrigen Zustellungen weiter

#### Scenario: Unbekannt loest einen Neuversand aus
- **WHEN** das Zielsystem den Status UNBEKANNT meldet
- **THEN** wird der Auftrag erneut ueber `direct:zustellen` zugestellt und das Ergebnis wie bei der Erstzustellung verbucht

### Requirement: Backoff mit Jitter und endgueltiges Scheitern
Der Backoff-Zeitpunkt MUSS als `basis * 2^(versuche - 1)` berechnet und auf einen konfigurierbaren Hoechstwert gedeckelt werden; hinzu KOMMT ein Jitter von bis zu 20 % des Backoff-Werts. Basis, Hoechstwert, Jitter-Anteil, max-versuche und Timer-Intervall MUESSEN konfigurierbar sein; im Test-Profil MUessen kurze Intervalle und eine kurze Lease gelten. Nach Erreichen der konfigurierbaren max-versuche MUSS die Zustellung als FEHLGESCHLAGEN verbucht und ein Fehler-Log geschrieben werden; die Zeile bleibt bestehen und wird nie geloescht.

#### Scenario: Backoff waechst exponentiell und ist gedeckelt
- **WHEN** eine Zustellung wiederholt ungeklaert bleibt und die versuche steigen
- **THEN** waechst der naechste Versuchszeitpunkt exponentiell mit den versuchen und ueberschreitet nie den Hoechstwert

#### Scenario: Jitter bleibt im Rahmen
- **WHEN** ein Backoff-Zeitpunkt berechnet wird
- **THEN** weicht er um hoechstens 20 % vom unberechneten Backoff-Wert ab

#### Scenario: Max-versuche machen die Zustellung endgueltig
- **WHEN** eine Zustellung die konfigurierten max-versuche erreicht hat und wieder scheitert
- **THEN** wird sie als FEHLGESCHLAGEN verbucht, ein Fehler-Log wird geschrieben und die Zeile nicht geloescht
