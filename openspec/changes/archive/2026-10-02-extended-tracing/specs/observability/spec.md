## ADDED Requirements

### Requirement: Spans je Camel-Routenschritt
Jeder Camel-Routenschritt MUSS einen eigenen Span im Trace erzeugen: die Route selbst sowie jeder darin durchlaufene Prozessorschritt (z. B. Validierung, Wahl des Zielsystems, Aufruf des Adapters, Kafka-Versand und -Konsum). Die Spans MUESSEN als Kinder des jeweiligen aufrufenden Kontexts im selben Trace liegen (Annahme-Request, Abgleich- oder Journal-Kontext).

#### Scenario: Zustellroute erscheint im Annahme-Trace
- **WHEN** die Annahme eine Zustellung ueber die Zustellroute ausfuehrt
- **THEN** liegen im Annahme-Trace eigene Spans fuer die Route und die durchlaufenen Prozessorschritte (Validierung und Adapter-Aufruf) unter dem Request-Span

#### Scenario: Abgleich-Route erscheint im Abgleich-Trace
- **WHEN** der Abgleich faellige Zustellungen verarbeitet
- **THEN** liegen die Routen- und Prozessorschritte des Statusabgleichs als eigene Spans unter dem Abgleich-Span

#### Scenario: Journal-Konsum erscheint im Indexier-Trace
- **WHEN** ein Journaleintrag konsumiert und indexiert wird
- **THEN** liegen Routen- und Prozessorschritte des Konsums als eigene Spans im Kontext des Indexier-Spans

### Requirement: Datenbankzugriffe im Trace
Jeder Datenbankzugriff des Services MUSS als eigener Span im Trace sichtbar sein. Der Span MUSS die angesprochene Operation (Lesen, Schreiben, Beanspruchen, Markieren) sowie die betroffene Tabelle als Attribute tragen. Lese- wie Schreibzugriffe der Annahme, Zustellungsverbuchung, Abgleich und Outbox MUESSEN erfasst sein.

#### Scenario: Annahme-Zugriffe liegen unter dem Request-Span
- **WHEN** ein Auftrag angenommen und persistiert wird
- **THEN** erscheinen die Datenbankzugriffe der Annahme als eigene Spans unter dem Annahme-Request-Span

#### Scenario: Verbuchung liegt im Zustell-Kontext
- **WHEN** eine Zustellung nach dem Adapter-Aufruf verbucht wird
- **THEN** erscheint der Schreibzugriff der Verbuchung als eigener Span im Kontext der Zustellung

#### Scenario: Abgleich-Zugriffe liegen im Abgleich-Span
- **WHEN** der Abgleich Zustellungen beansprucht, Status abfragt und neu verbucht
- **THEN** erscheinen diese Datenbankzugriffe als eigene Spans unter dem Abgleich-Span

### Requirement: Externe Systemaufrufe sind im Trace erkennbar
Jeder Aufruf eines externen Systems MUSS als eigener Client-Span im Trace erscheinen und sich von der Verarbeitungslogik abheben: Zustellung und Statusabfrage ans Imperium (SOAP) bzw. an die Rebellion (REST) tragen das Zielsystem, der Journaleintrag-Versand nach Kafka sowie die Indexierung in OpenSearch laufen als eigene Spans. Ein Misserfolg (z. B. Timeout, SOAP-Fault, HTTP-Fehler) MUSS sich im Span-Status (Fehler samt Ausnahme-Attribut) widerspiegeln.

#### Scenario: Imperium-Zustellung erscheint als Client-Span
- **WHEN** eine Zustellung an das Imperium uebergeben wird
- **THEN** erscheint der SOAP-Aufruf als eigener Span mit Zielsystem-Attribut IMPERIUM im Annahme-Trace, abgesetzt von den Spans der Verarbeitungslogik

#### Scenario: Rebellion-Zustellung erscheint als Client-Span
- **WHEN** eine Zustellung an die Rebellion uebergeben wird
- **THEN** erscheint der REST-Aufruf als eigener Span mit Zielsystem-Attribut REBELLION im Annahme-Trace, abgesetzt von den Spans der Verarbeitungslogik

#### Scenario: Fehlerhafter externer Aufruf ist als solcher erkennbar
- **WHEN** ein externer Aufruf fehlschlaegt (Timeout, SOAP-Fault oder HTTP-Fehler)
- **THEN** traegt der zugehoerige Span den Fehler-Status mit Ausnahme-Attribut und bleibt dem Zustell-Kontext zugeordnet

#### Scenario: Nachvollziehbarkeit eines Auftrags Ende-zu-Ende
- **WHEN** ein Auftrag angenommen, zugestellt, verbucht und journaleintrag-Verarbeitung durchlaufen wurde
- **THEN** laesst sich im Trace von der Annahme ueber jeden Verarbeitungs- und Datenbank-Schritt bis zum externen Aufruf und zur Journal-Verarbeitung jede Phase als Span nachvollziehen
