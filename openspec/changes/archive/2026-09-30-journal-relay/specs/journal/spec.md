## Purpose

Verschreibt den fachlichen Vertrag der Journal-Kette: zuverlaessiger Versand der Outbox-Journaleintraege nach Kafka, Indexierung der Eintraege in OpenSearch sowie die Komponentengrenze zwischen kaufauftrag und journal.

## ADDED Requirements

### Requirement: Outbox-Relay versendet Journaleintraege zuverlaessig
Der Journal-Relay MUSS periodisch ungesendete `journal_outbox`-Zeilen in Batches atomar beanspruchen (`FOR UPDATE SKIP LOCKED`), jeden Payload an das Kafka-Topic `durchlauferhitzer.journal` senden — mit der Auftrags-ID als Nachrichten-Key, `acks=all`, idempotentem Producer und synchroner Wartezeit auf die Bestaetigung — und danach `gesendet_am` setzen. Doppelte Sendungen nach einem Absturz sind erlaubt (at-least-once).

#### Scenario: Ungesendete Zeile wird versendet und verbucht
- **WHEN** eine journal_outbox-Zeile mit ungesetztem gesendet_am existiert
- **THEN** versendet der Relay ihren Payload an das Topic durchlauferhitzer.journal (Key = Auftrags-ID) und setzt gesendet_am erst nach der bestaetigten Sendung

#### Scenario: Batches ohne Doppelvergabe
- **WHEN** zwei Relay-Instanzen gleichzeitig ungesendete Zeilen beanspruchen
- **THEN** bekommt jede Zeile hoechstens eine Instanz (FOR UPDATE SKIP LOCKED)

#### Scenario: Absturz nach Sendung ist tolerierbar
- **WHEN** der Relay zwischen bestaetigter Sendung und dem Setzen von gesendet_am abstuerzt
- **THEN** wird die Zeile spaeter erneut versendet — eine Duplikatsendung ist erlaubt

### Requirement: Kafka-Ausfall staut die Outbox ohne Datenverlust
Ist Kafka nicht erreichbar, MUSS die Auftragsannahme unveraendert weiterlaufen. Der Relay MUSS den Versand ohne Datenverlust ablehnen (kein gesendet_am wird gesetzt) und den Rueckstand nachholen, sobald Kafka wieder erreichbar ist.

#### Scenario: Annahme laeuft bei Kafka-Ausfall weiter
- **WHEN** Kafka bei einer Annahme nicht erreichbar ist
- **THEN** antwortet die Annahme wie gewohnt und der Journaleintrag bleibt in der Outbox ungesendet liegen

#### Scenario: Rueckstand wird nachgeholt
- **WHEN** Kafka wieder erreichbar ist und ungesendete Zeilen existieren
- **THEN** versendet der Relay den aufgelaufenen Bestand

### Requirement: Journal-Indexierung in OpenSearch mit Commit nach Erfolg
Eine Consumer-Route MUSS das Topic `durchlauferhitzer.journal` konsumieren und jeden Journaleintrag in den OpenSearch-Index `durchlauferhitzer-journal` indexieren, mit der Auftrags-ID als Dokument-ID. Der Kafka-Commit MUSS erst nach erfolgreichem Indexieren erfolgen; ein Indexierfehler fuehrt zu keinem Commit, sodass der Eintrag erneut konsumiert wird. Die Indexierung mit derselben Dokument-ID MUSS idempotent sein (das Dokument wird ueberschrieben).

#### Scenario: Journaleintrag erscheint im Index
- **WHEN** ein Auftrag mit Trace-ID angenommen und versendet wurde
- **THEN** existiert im Index durchlauferhitzer-journal ein Dokument mit der Auftrags-ID als Dokument-ID und der Trace-ID

#### Scenario: Indexierfehler verhindert den Commit
- **WHEN** das Indexieren eines Eintrags scheitert
- **THEN** wird der Kafka-Offset nicht committet und der Eintrag erneut konsumiert

#### Scenario: Doppelte Zustellung bleibt idempotent
- **WHEN** derselbe Journaleintrag zweimal zugestellt wird
- **THEN** existiert danach genau ein Dokument mit dieser Auftrags-ID (ueberschrieben, kein Duplikat)

### Requirement: Index mit explizitem Mapping, rohPayload nicht indiziert
Beim Start MUSS der Index `durchlauferhitzer-journal` angelegt werden, falls er noch nicht existiert, und zwar mit einem expliziten Mapping. Das Feld `rohPayload` DARF dabei nicht indiziert werden.

#### Scenario: Index existiert nach dem Start mit Mapping
- **WHEN** der Service gegen eine frische OpenSearch-Instanz startet
- **THEN** existiert der Index durchlauferhitzer-journal mit dem expliziten Mapping (Auftrags-ID als Dokument-ID, Felder des Journaleintrags)

#### Scenario: rohPayload ist nicht durchsuchbar
- **WHEN** im Index nach Inhalten des rohen Payloads gesucht wird
- **THEN** liefert die Suche keine Treffer ueber rohPayload (das Feld ist nicht indiziert)

### Requirement: Komponentengrenze zwischen kaufauftrag und journal
Die Outbox-Zeile MUSS von der Komponente kaufauftrag in ihrer Annahme-Transaktion geschrieben werden; Relay und Indexierung MUSS die Komponente journal besitzen. Die Kopplung der beiden Komponenten MUSS ausschliesslich ueber das Tabellenformat von journal_outbox laufen; eine Compile-Abhaengigkeit zwischen den Komponenten DARF nicht entstehen.

#### Scenario: Outbox-Schreibung bleibt Teil der Annahme-Transaktion
- **WHEN** ein Auftrag angenommen wird
- **THEN** schreibt die Komponente kaufauftrag den Journaleintrag innerhalb der Annahme-Transaktion (alles-oder-nichts mit auftrag und zustellung)

#### Scenario: Keine Compile-Abhaengigkeit zwischen den Komponenten
- **WHEN** die Architektur betrachtet wird
- **THEN** haengt die Komponente journal an keiner Klasse der Komponente kaufauftrag und umgekehrt (erzwungen durch Architekturtest)
