# Zustellungssteuerung Specification

## Purpose

Steht für den fachlichen Ablauf von der angenommenen Bestellung bis zur verbuchten Zustellung: Auswahl des Zielsystems per Feature-Flag, genau eine Zustellung pro Auftrag nach dem Commit, Verbuchung des Ergebnisses über die Zustandsmaschine.

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

### Requirement: Verbuchung mit genau einem erwarteten Update
Nach der Zustellung MUSS die Zustellungssteuerung pro Zustellungszeile GENAU EIN Update per Primärschlüssel ausfuehren, und NUR wenn der Ausgangsstatus der erwartete ist (IN_ZUSTELLUNG): bei Erfolg auf BESTAETIGT mit externer Referenz, bei Misserfolg (ZustellungUngeklaert) auf UNGEKLAERT mit naechster_versuch_um. Der Adapter MUSS selbst nichts verbuchen.

#### Scenario: Erfolg wird als BESTAETIGT verbucht
- **WHEN** der Adapter eine Zustellbestaetigung mit externer Referenz liefert
- **THEN** wird die Zustellungszeile per Primärschlüssel auf BESTAETIGT mit der externen Referenz aktualisiert

#### Scenario: Misserfolg wird als UNGEKLAERT verbucht
- **WHEN** der Adapter ZustellungUngeklaert wirft
- **THEN** wird die Zustellungszeile per Primärschluessel auf UNGEKLAERT mit naechster_versuch_um aktualisiert

#### Scenario: Kein Update bei unerwartetem Ausgangsstatus
- **WHEN** die Zustellungszeile bei dem Update nicht mehr den erwarteten Ausgangsstatus IN_ZUSTELLUNG hat
- **THEN** wird keine Statusaenderung geschrieben

### Requirement: Zustandsmaschine im Aggregat Kaufauftrag
Der Auftrag MUSS als Aggregat `Kaufauftrag` mit seinen Zustellungen modelliert sein und die Zustaende der Zustellungen verwalten: IN_ZUSTELLUNG → BESTAETIGT oder IN_ZUSTELLUNG → UNGEKLAERT in diesem Change. Die zusaetzlich vorhandenen Zustaende IN_ABGLEICH und FEHLGESCHLAGEN MUESSEN im Schema und der Zustandsmaschine vorhanden, in diesem Change aber noch nicht erreichbar sein.

#### Scenario: Zustandsuebergang bei Erfolg
- **WHEN** die Zustellung einer IN_ZUSTELLUNG-Zeile erfolgreich ist
- **THEN** geht das Aggregat diese Zustellung in BESTAETIGT ueber

#### Scenario: Reservierte Zustaende sind vorhanden
- **WHEN** die Zustandsmaschine des Aggregats betrachtet wird
- **THEN** umfasst sie IN_ZUSTELLUNG, IN_ABGLEICH, UNGEKLAERT, BESTAETIGT und FEHLGESCHLAGEN, wobei IN_ABGLEICH und FEHLGESCHLAGEN keine Uebergaenge in diesem Change haben

### Requirement: Zustellungszeilen mit Lease, nie Loeschungen
Bei der Annahme MUSS pro gewaehltem Zielsystem eine Zustellungszeile als IN_ZUSTELLUNG mit Lease (lease_bis, instanz) angelegt werden. Zustellungs- und Auftragszeilen DUERFEN NIE geloescht werden.

#### Scenario: Zustellungszeile mit Lease
- **WHEN** ein Auftrag mit Zielsystem imperium angenommen wird
- **THEN** existiert danach genau eine Zustellungszeile (auftrags_id, imperium) mit status IN_ZUSTELLUNG und gesetztem lease_bis sowie instanz

#### Scenario: Keine Loeschung
- **WHEN** eine Zustellung bestaetigt oder fuer ungeklaert erklaert wurde
- **THEN** bleibt die Zeile in der Datenbank bestehen
