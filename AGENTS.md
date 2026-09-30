# AGENTS.md

Anweisungen fuer Coding-Agents in diesem Repository.

## Sprache

- Deutsch fuer Doku, Kommunikation und OpenSpec-Artefakte; Englisch fuer Code-Bezeichner.

## Werkzeuge und Umgebung

- Alles Tooling laeuft ueber devenv: Shell mit `devenv shell` (oder `direnv allow` + Shell-Neustart).
- devenv liegt in `~/.nix-profile/bin/`; wenn `devenv` nicht gefunden wird, PATH entsprechend erweitern.
- In IDE-Integrationen (z. B. VSCode-Extension mit eigenem `LD_LIBRARY_PATH`) muessen `openspec` und `devenv` mit bereinigtem Environment laufen: `env -u LD_LIBRARY_PATH openspec ...` bzw. `env -u LD_LIBRARY_PATH devenv ...` — sonst bricht die Ausfuehrung mit GLIBCXX-Fehlern ab.

## Entwicklungsumgebung

- Dienste (Postgres `raumschiffwerft`/`raumschiffwerft`, Kafka, OpenSearch, OTel-Collector OTLP 4317/4318, WireMock 8089): `devenv up -d`, Status `devenv processes status <name>`, Stop `devenv processes down`.
- WireMock-Stubs: eine JSON-Datei pro Stub unter `wiremock/mappings/`.
- JDK: GraalVM CE 25 (inkl. `native-image`); Java-Release 25.

## Build und Tests

- `mvn verify` im Wurzelverzeichnis (in der devenv-Shell) baut alle vier Module.
- Unit-Tests `*Test` via Surefire: JUnit 5 + Mockito, KEIN `@QuarkusTest`.
- Integrationstests `*IT` via Failsafe (service und Adapter): `@QuarkusTest` erlaubt.
- Architekturtests (ArchUnit) laufen im `raumschiffwerft-service` mit und erzwingen Modul-Layering (model <- adapter <- service), interne BCE-Regeln und die Camel-Freiheit der Adapter — Verstoesse lassen den Build scheitern; Regeln nicht abschwaechen, ohne die Specs zu aktualisieren.
- Native-Kompilierung: `mvn -Pnative package` (nur `raumschiffwerft-service`).

## Arbeitsweise: OpenSpec

- Jede fachliche Aenderung laeuft als OpenSpec-Change: proposal, specs (Delta), design, tasks (`openspec new change <name>`; Workflows als Skills in `.vibe/skills/` bzw. `openspec instructions`).
- Projektkontext (Stack, Architektur, Fachlichkeit, Testkonventionen) steht in `openspec/config.yaml` und wird beim Planen automatisch beruecksichtigt.
- Dokumentierter Ist-Stand des Systems: `openspec/specs/` (Haupt-Specs). Abgeschlossene Changes inkl. Entscheidungen: `openspec/changes/archive/`.
- Nach Abschluss eines Changes: Specs syncen und Change archivieren — die Haupt-Specs sind das Gedaechtnis, nicht der Chatverlauf.
