## Purpose

Verschreibt den fachlichen Vertrag der Aufbewahrung und Loeschung: periodisches Aufraeumen durch genau eine Instanz, Batchloeschung gesendeter Journaleintraege und abgeschlossener Auftraege, Aufbewahrungsfrist als Idempotenzfenster sowie die Unantastbarkeit fehlgeschlagener Auftraege.

## ADDED Requirements

### Requirement: Aufraeum-Durchlauf mit Advisory-Lock
Das Aufraeumen MUSS periodisch durch eine Timer-Route angestossen werden (konfigurierbares Intervall, Default 10 Minuten). Zu Beginn jedes Durchlaufs MUSS sich die Instanz mit `pg_try_advisory_lock` absichern; ist die Sperre bereits von einem anderen Pod gehalten, MUSS der Durchlauf sofort und ohne Loeschung abbrechen. Die Sperre MUSS fuer den gesamten Durchlauf gehalten und am Ende wieder freigegeben werden.

#### Scenario: Freie Sperre erlaubt den Durchlauf
- **WHEN** der Timer feuert und die Advisory-Sperre frei ist
- **THEN** raeumt die Instanz in diesem Durchlauf auf und gibt die Sperre am Ende frei

#### Scenario: Belegte Sperre bricht ab
- **WHEN** der Timer feuert, aber ein anderer Pod haelt die Advisory-Sperre
- **THEN** bricht der Durchlauf sofort ab, ohne dass irgendetwas geloescht wird

### Requirement: Batchloeschung gesendeter Journaleintraege
Das Aufraeumen MUSS gesendete `journal_outbox`-Zeilen (gesendet_am gesetzt) in Batches von hoechstens 1000 Zeilen pro Durchlauf loeschen. Ungesendete Zeilen MUESSEN bleiben, bis der Journal-Relay sie versendet hat.

#### Scenario: Gesendete Zeilen verschwinden
- **WHEN** der Aufraeum-Durchlauf laeuft und journal_outbox-Zeilen sind gesendet
- **THEN** werden sie in Batches von hoechstens 1000 Zeilen geloescht

#### Scenario: Ungesendete Zeilen bleiben
- **WHEN** eine journal_outbox-Zeile noch kein gesendet_am hat
- **THEN** bleibt sie unberuehrt

### Requirement: Aufbewahrungsregel fuer Auftraege
Auftraege samt ihren Zustellungszeilen MUESSEN geloescht werden, wenn ihre Annahme aelter ist als die konfigurierbare Aufbewahrungsfrist (`durchlauferhitzer.aufraeumen.aufbewahrung`, Default 7 Tage) und ALLE ihre Zustellungen BESTAETIGT sind — in Batches von hoechstens 1000 Auftraegen pro Durchlauf. Auftraege mit mindestens einer Zustellung in FEHLGESCHLAGEN MUeSSEN NIE automatisch geloescht werden. Die Aufbewahrungsfrist IST zugleich das Idempotenzfenster: nach der Loeschung fuehrt derselbe Idempotency-Key zu einer Neuanname.

#### Scenario: Abgeschlossener Auftrag verschwindet nach Ablauf
- **WHEN** ein Auftrag aelter als die Aufbewahrungsfrist ist und alle seine Zustellungen BESTAETIGT sind
- **THEN** werden seine auftrag- und zustellung-Zeilen geloescht

#### Scenario: Fehlgeschlagener Auftrag bleibt fuer immer
- **WHEN** ein Auftrag eine Zustellung in FEHLGESCHLAGEN hat
- **THEN** wird er vom Aufraeumen nie geloescht, egal wie alt er ist

#### Scenario: Offene oder ungeklaerte Zustellungen halten den Auftrag
- **WHEN** ein Auftrag eine Zustellung in IN_ZUSTELLUNG, IN_ABGLEICH oder UNGEKLAERT hat
- **THEN** bleibt der Auftrag unberuehrt, weil nicht alle Zustellungen BESTAETIGT sind

#### Scenario: Aufbewahrungsfrist ist das Idempotenzfenster
- **WHEN** ein Auftrag geloescht wurde und derselbe Idempotency-Key erneut gepostet wird
- **THEN** wird der Auftrag neu angenommen (keine Rueckgabe eines geloeschten Standes)

### Requirement: Partielle Indizes stuetzen die Loeschabfragen
Die Kandidatensuche der Loeschabfragen MUSS durch partielle Indizes gestuetzt werden: auf `journal_outbox` fuer gesendete Zeilen und auf `zustellung` fuer nicht-bestaetigte Zeilen (Anforderung in auftragsspeicherung, Flyway-Migration V2).

#### Scenario: Kandidatensuche ohne Vollscan
- **WHEN** der Aufraeum-Durchlauf Kandidaten sucht
- **THEN** nutzen die Abfragen die partiellen Indizes statt die Tabellen voll zu scannen
