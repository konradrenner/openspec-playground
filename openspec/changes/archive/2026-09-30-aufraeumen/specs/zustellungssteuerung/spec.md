## MODIFIED Requirements

### Requirement: Zustellungszeilen mit Lease, nie Loeschungen
Bei der Annahme MUSS pro gewaehltem Zielsystem eine Zustellungszeile als IN_ZUSTELLUNG mit Lease (lease_bis, instanz) angelegt werden. Loeschungen von Zustellungs- und Auftragszeilen gibt es NUR durch das Aufraeumen (Faehigkeit aufraeumen) und nur gemaess der Aufbewahrungsregel: Auftraege, deren Zustellungen alle BESTAETIGT sind und deren Aufbewahrungsfrist abgelaufen ist. Jede andere Komponente DARF keine Zeilen loeschen; Auftraege mit einer Zustellung in FEHLGESCHLAGEN werden von keinem automatischen Aufraeumen geloescht.

#### Scenario: Zustellungszeile mit Lease
- **WHEN** ein Auftrag mit Zielsystem imperium angenommen wird
- **THEN** existiert danach genau eine Zustellungszeile (auftrags_id, imperium) mit status IN_ZUSTELLUNG und gesetztem lease_bis sowie instanz

#### Scenario: Keine Loeschung
- **WHEN** eine Zustellung bestaetigt oder fuer ungeklaert erklaert wurde
- **THEN** bleibt die Zeile bestehen, solange die Aufbewahrungsfrist des Auftrags nicht abgelaufen ist oder nicht alle Zustellungen BESTAETIGT sind; geloescht wird ausschliesslich durch das Aufraeumen gemaess der Aufbewahrungsregel
