## Context

Die Annahme schreibt Journaleintraege (schemaVersion, auftragsId, empfangenAm, traceId, traceparent, rohPayload, kaufauftrag) als jsonb in `journal_outbox` (Insert durch `OutboxRepository` innerhalb der Annahme-Transaktion); `gesendet_am` existiert, bleibt aber ungesetzt. Kafka (9092, `services.kafka.enable`) und OpenSearch (9200, `services.opensearch.enable`, 3.5.0, ohne Auth) laufen via devenv; `kafka.bootstrap.servers` ist konfiguriert. Die Architekturtests erzwingen Modul-Layering und BCE; die Moeglichkeit, Kafka/OpenSearch-Routen in boundary zu legen, ist durch `ZustellRoute`/`AbgleichRoute` vorgezeichnet. Siehe proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- At-least-once-Versand der Outbox nach Kafka (Batches, SKIP LOCKED, acks=all, idempotenter Producer, gesendet_am erst nach Ack).
- Idempotente Indexierung in OpenSearch mit Dokument-ID = Auftrags-ID, Kafka-Commit erst nach erfolgreicher Indexierung; explizites Index-Mapping ohne rohPayload-Indizierung.
- Komponente journal neben kaufauftrag, Kopplung ausschliesslich ueber das Tabellenformat, per Architekturtest erzwungen.

**Non-Goals:**

- Keine Transaktionalitaet ueber Kafka hinaus (kein exactly-once, keine Deduplizierung auf Consumer-Seite noetig — Dokument-ID macht idempotent).
- Kein Schema-Mapping-/Payload-Wandel (keine Flyway-Migration, Tabellenformat bleibt unverletzt).
- Kein Uebersetzten bestehender kaufauftrag-Klassen (kein Umbau auf neue Packages); keine REST-/Query-API auf dem Journal (nur OpenSearch direkt).
- Kein Kafka-Ausfall-IT mit gestopptem Broker (unit-seitig abgedeckt); kein OTel-Export der Journal-Daten.

## Decisions

### D1: Komponente journal als eigene Sub-Packages plus Architekturtest

- Neue Packages: `service.control.journal` (Steuerung: `JournalRelay`, Lese-/Markier-Repository `JournalOutboxRepository`, `JournalIndexer`) und `service.boundary.journal` (Camel-Routen, Kafka- und OpenSearch-Anbindung). Die BCE-Regeln greifen weiterhin (Patterns `..control..`/`..boundary..` matchen Sub-Packages).
- Die bestehenden Klassen bleiben, wo sie sind, und bilden implizit die Komponente kaufauftrag — minimaler Diff.
- Kopplung nur ueber die Tabelle: journal liest `journal_outbox` (SELECT ... SKIP LOCKED, UPDATE gesendet_am) mit eigenem JDBC-Code; kaufauftrag schreibt wie bisher mit `OutboxRepository` in der Annahme-Transaktion. journal braucht keinerlei Klasse aus kaufauftrag (Payload ist ein jsonb-String, Auftrags-ID ein UUID-String).
- Neue ArchUnit-Regeln (`ComponentArchitectureTest`): Klassen in `..service..journal..` duerfen nicht von Klassen ausserhalb `..service..journal..` (ausser `model`/`entity`-Werteobjekten und JDK) abhaengen; umgekehrt darf nichts ausserhalb journal von journal-Klassen abhaengen. Ausnahmen bewusst nicht vorgesehen.

### D2: Relay — Timer-Route mit Batch-Transaktion und synchronem Ack

- `JournalRelayRoute` (boundary.journal): `from("timer:journal-relay?period=...")` → `JournalRelay.relay()`.
- `JournalRelay` (control.journal) pro Durchlauf: eine kurze Transaktion — `SELECT id, auftrags_id, payload FROM journal_outbox WHERE gesendet_am IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED`, je Zeile synchroner Versand ueber den Port `JournalVersand` (Interface in control.journal, Implementierung in boundary.journal mit ProducerTemplate auf eine `direct:journalVersenden`-Route), danach `UPDATE ... SET gesendet_am = now()` fuer die gesendeten Zeilen, Commit. Sendefehler: Rollback (kein gesendet_am), Rest des Batches im naechsten Durchlauf; bereits gesendete Zeilen duerfen erneut gesendet werden (at-least-once, erlaubt).
- Kafka-Endpoint in der Route: `to("kafka:{{journal.topic}}?brokers={{kafka.bootstrap.servers}}&acks=all")`; Nachrichten-Key ueber den Header `kafka.KEY` = Auftrags-ID (String). Idempotenter Producer ueber die Komponenten-Konfiguration (`camel.component.kafka.configuration.enable-idempotence=true`, Vorgabe des kafka-clients seit 3.x, hier explizit). Der Camel-Kafka-Producer wartet synchron auf das Ack der Broker (acks=all).

### D3: Consumer-Route — Indexieren, dann committen

- `JournalConsumerRoute` (boundary.journal): `from("kafka:{{journal.topic}}?brokers={{kafka.bootstrap.servers}}&groupId={{journal.consumer-group}}&autoCommitDisable=true&allowManualAcks=true&autoOffsetReset=earliest")` → `JournalIndexer.indexiere(auftragsId, payload)` → danach der manuelle Ack (`KafkaManualAck` aus dem Exchange-Header), sodass der Offset erst nach erfolgreicher Indexierung committet wird. Indexierfehler laesst die Route fehlschlagen; der Eintrag wird nach Rücksicherung erneut konsumiert.
- `JournalIndexer` (control.journal) haengt am Port `JournalIndex` (Interface in control.journal, Implementierung `OpenSearchIndexClient` in boundary.journal): `PUT /{index}/_doc/{auftragsId}` mit dem Payload als Body. Dokument-ID = Auftrags-ID macht Wiederholungen idempotent (ueberschreibt).
- OpenSearch-Anbindung als Quarkus-REST-Client (`quarkus-rest-client` + Jackson, im Stack bereits ueber den Rebellion-Adapter erprobt): `@RegisterRestClient`, Basis-URL `journal.opensearch.url`. Bewusst kein schwerer OpenSearch-Java-Client — benoetigt werden nur drei Aufrufe (Index anlegen, Index pruefen, Dokument speichern). Alternative Quarkiverse-`quarkus-opensearch`-Extension verworfen: zusaetzliche Abhaengigkeit fuer drei REST-Aufrufe (KISS).

### D4: Index-Mapping beim Start, rohPayload nicht indiziert

- Beim Start (`@Observes StartupEvent` in einer boundary.journal-Bean): Index per `GET /{index}` pruefen; fehlt er, per `PUT /{index}` mit explizitem Mapping anlegen: `schemaVersion` (long), `auftragsId` (keyword), `empfangenAm` (date), `traceId` (keyword), `traceparent` (keyword), `kaufauftrag` (object, dynamisch), `rohPayload` mit `"enabled": false` (nicht indizierbar, aber im Dokument gespeichert).
- Existiert der Index bereits (z. B. aus frueheren Laeufen), bleibt er unangetastet — das Mapping ist deterministisch, eine Abweichung wuerde beim naechsten Start auffallen.

### D5: Konfiguration und Test-Profil

- Neu: `journal.topic=durchlauferhitzer.journal`, `journal.index=durchlauferhitzer-journal`, `journal.opensearch.url=http://localhost:9200`, `journal.consumer-group=durchlauferhitzer-journal`, `journal.relay.timer-period-millis=2000`, `journal.relay.batch=50`, `camel.component.kafka.configuration.enable-idempotence=true`.
- Test-Profil: Timer-Periode 500 ms, Batch 50; OpenSearch/Kafka wie in devenv.

### D6: Bestehende Tests und neue ITs

- `AnnahmeIT.wiederholteAnnahmeSchreibtKeinDuplikat` behauptet `gesendet_am IS NULL` — wird auf reine Existenz des Journaleintrags (count = 1) umgestellt, da der Versand asynchron laeuft.
- Abgleich-ITs: unberuehrt; sie raeumen die Tabellen ohnehin vor jedem Test (auch `journal_outbox`), zurueckbleibende Kafka-Nachrichten stoeren dort nicht (keine Kafka-Asserts).
- Neue `JournalRelayIT` (`@QuarkusTest`): Annahme per POST mit `Idempotency-Key` und festem `traceparent`-Header (gueltige W3C-Trace-ID) → mit Awaitility auf das OpenSearch-Dokument `GET /durchlauferhitzer-journal/_doc/{auftragsId}` warten und `traceId` im `_source` pruefen; anschliessend `gesendet_am` in der DB pruefen. Der Index wird in der IT nicht geloescht (Dokument-IDs sind je Lauf eindeutig; Loeschen haette Default-Mapping statt explizitem Mapping zur Folge).
- Unit-Tests (kein `@QuarkusTest`): `JournalRelay` (Beanspruchen/Senden/Verbuchen mit gemocktem Repository und Port; Sendefehler → kein gesendet_am, Batch bricht nicht die Annahme), `JournalIndexer` (Indexierfehler → kein Commit; Erfolg → Commit), Mapping-Builder falls nicht als Konstante.

## Risks / Trade-offs

- [Topic muss existieren oder Auto-Create erlaubt sein] → devenv-Kafka (KRaft) legt Topics per Default automatisch an; faellt das aus, ist das Topic einmalig anzulegen (Betriebsregel, kein Code-Abhängig).
- [Batch-Commit nach allen Sends: Absturz nach Teilversand erzeugt Duplikate] → bewusst at-least-once; Consumer-Seite ist durch die Dokument-ID idempotent.
- [Relay liefert beim ersten Lauf alle Alt-Zeilen aus frueheren Integrationstests] → Testdaten sind in devenv irrelevant; die DBs sind nicht produktiv. Die Annahme-IT-Bereinigung bleibt wie sie ist.
- [OpenSearch-Dokumente aus frueheren Laeufen bleiben] → unschaedlich (Lookup je Test ueber eindeutige, zufaellige Auftrags-IDs); ein Aufräumen wuerde nur fuer die ITs Code erfordern, der nicht ins Produktivverhalten gehoert.
- [Explizites Mapping aendert sich spaeter] → Index-Existenz-Check schuetzt vor Konflikt beim Start, aber nicht vor Drift; ein Mapping-Wandel gehoert in einen eigenen Change (Index-Reindex oder Version-Suffix).
- [Kafka-Consumer-Gruppe mit fixem Offset-Stand] → `autoOffsetReset=earliest` nur wirksam, wenn noch kein Offset existiert; fuer die ITs ist das irrelevant, da jede IT ihre eigene Auftrags-ID mitbringt.

## Migration Plan

Rein additiv: keine Migration, keine Daten- oder Vertragsaenderung; der Relay beginnt, den ungesendeten Bestand abzuliefern (der gewuenschte Effekt). Ausrollen = Deploy. Rollback = Deploy des alten Stands — versandte Eintraege bleiben versendet (gesendet_am gesetzt), ungesendete warten weiter; Kafka und OpenSearch behalten ihre Daten.

## Umsetzungskorrekturen (Apply-Phase)

- Camel-Kafka-API (4.18): Der URI-Parameter heisst `requestRequiredAcks=all` (nicht `acks`), der Consumer nutzt `autoCommitEnable=false` + `allowManualCommit=true` und committet manuell ueber den Header `CamelKafkaManualCommit` (`KafkaManualCommit.commit()`); `allowManualAcks`/`KafkaManualAck` existieren in dieser Camel-Version nicht.
- Der Quarkus-REST-Client (reactive) wirft trotz `Response`-Rueckgabe bei 404 eine `WebApplicationException`: `JournalIndexVerwaltung` behandelt 404 explizit als „Index fehlt" und faengt alle Startfehler mit Warn-Log ab — der Start blockiert nie, auch nicht bei ausgefallenem OpenSearch.
- `JournalOutboxRepository` ist eine zustandslose Klasse ohne CDI, die der Relay selbst instanziiert (fuer Unit-Tests mit zweitem paket-privatem Konstruktor injizierbar); das Relay markiert jede Zeile unmittelbar nach ihrem Versand innerhalb derselben Transaktion.
