## 1. Camel-Spans

- [x] 1.1 `camel-quarkus-opentelemetry` als Abhaengigkeit im `raumschiffwerft-service` ergaenzen; pruefen, dass der Tracer sich an die CDI-`OpenTelemetry`-Instanz bindet. Verifikation: Dev-Start, im Collector-Log/Zipkin erscheinen Routen-Spans (`zustellen`, `abgleich`, `statusAbfragen`, `journal-relay`, `journalVersenden`, Journal-Consumer) als Kinder des jeweiligen Kontexts.
- [x] 1.2 Span-Namen pruefen: routeIds sind bereits gesetzt (`zustellen`, `abgleich`, `statusAbfragen`, `journal-relay`, `journalVersenden`) — fehlende ergaenzen oder Einstellungen dokumentieren. Verifikation: Stichprobe im Trace-Bild einer Annahme (Route- und Processor-Spans unter dem Request-Span).

## 2. DB-Spans

- [x] 2.1 Connection-Wrapper in `Verbindungen` umsetzen: Proxy um jede gelieferte Connection, der `executeQuery`/`executeUpdate`/`execute` als Span `db.zugriff` mit `db.sql`, `db.operation` und `db.system=postgresql` oeffnet; Transaktionsverbindung einmalig wrappen. Verifikation: neuer Unit-Test des Wrappers mit SDK-losen Test-Double bzw. In-Memory-Aussagen (Spans erzeugt je Statement-Ausfuehrung).
- [x] 2.2 Span `db.transaktion` im Transaktionsverwalter ergaenzen (Commit/Rollback sichtbar, Name der Transaktion als Attribut). Verifikation: Unit-Test des Transaktionsverwalters bleibt gruen bzw. wird um Span-Aussage ergaenzt.
- [x] 2.3 Nachweis im IT: `ObservabilityIT` um eine Annahme-Pruefung ergaenzen — DB-Spans liegen im Trace (z. B. via Test-SDK-InMemory-Exporter in der Quarkus-Test-Umgebung); alternativ dokumentierter manueller Nachweis im Collector-Log, wenn ein SDK-Exporter im IT unvertretbar ist. Verifikation: `mvn verify` gruen, DB-Spans im Trace einer Annahme sichtbar.

## 3. Adapter-Spans

- [x] 3.1 OpenTelemetry-API-Abhaengigkeit in beide Adapter ergaenzen (`opentelemetry-api`, ohne SDK) und in `ImperiumZielsystem` sowie `RebellionZielsystem` Client-Spans `adapter.<zielsystem>.<operation>` mit `durchlauferhifter.zielsystem` und `durchlauferhitzer.auftrag.id` setzen; Fehler mit `recordException` + Status ERROR. Verifikation: Unit-Tests der Adapter (Tracer-Double) pruefen Span-Erzeugung und Fehler-Markierung; ArchUnit laeuft weiter.
- [x] 3.2 Pruefen, ob der CXF-SOAP-Client zusaetzlich automatische Spans erzeugt (Open Question aus design.md) — Ergebnis im README dokumentieren. Verifikation: Trace-Bild einer Imperium-Zustellung im Collector-Log/Zipkin zeigt den Adapter-Span mit Zielsystem-Attribut und — falls vorhanden — einen HTTP-Kind-Span.

## 4. Nachvollziehbarkeit und Absicherung

- [x] 4.1 Ende-zu-Ende-Nachweis: Dev-Start (`devenv up -d` + `mvn -pl raumschiffwerft-service quarkus:dev`), Annahme mit `traceparent`-Header, Trace im Collector-Log/Zipkin (Suche nach Trace-ID) verifizieren: Request-Span, Routen-/Processor-Spans, DB-Spans, Adapter-Span, Relay-/Indexier-Spans — alle in einem Trace.
- [x] 4.2 `mvn verify` (Unit, ArchUnit, ITs) gruen; danach `e2e` laufen lassen (Szenarien unveraendert gruen, Service-Log `/tmp/raumschiffwerft-e2e.log` ohne neue Fehler).
- [x] 4.3 README-Abschnitt Observability aktualisieren: erweitertes Trace-Bild (Routen-, DB- und Adapter-Spans) und Suche in Zipkin beschreiben. Verifikation: README-Review, Stichprobe mit den dokumentierten Befehlen funktioniert.
