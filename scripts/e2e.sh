#!/usr/bin/env bash
# End-to-End-Simulation der Raumschiffwerft gegen die devenv-Dienste
# (Postgres, WireMock, Kafka, OpenSearch, OTel-Collector muessen laufen).
#
#   devenv up -d && e2e
#
# Der Start baut den Runner nur, wenn er fehlt (rm -rf raumschiffwerft-service/target
# erzwingt einen Neubau). Fuer schnelles Konvergieren laeuft der Service mit kurzen
# Intervallen fuer Abgleich/Backoff/Lease/Journal-Relay (nur Test-overrides,
# das Prod-Profil bleibt unberuehrt).
set -uo pipefail

SERVICE="http://localhost:8080"
WIREMOCK="http://localhost:8089"
OPENSEARCH="http://localhost:9200"
export SERVICE # fuer die in bash -c-Sub-Shells laufenden Funktionen
RUNNER="raumschiffwerft-service/target/quarkus-app/quarkus-run.jar"
SERVICE_PID=""

PASS=0
FAIL=0

rot()   { printf '\033[31m%s\033[0m\n' "$1"; }
gruen() { printf '\033[32m%s\033[0m\n' "$1"; }
gelb()  { printf '\033[33m%s\033[0m\n' "$1"; }

# ---------------------------------------------------------------- Hilfsmittel

pruefe() { # pruefe "Beschreibung" <befehl...>
  local beschreibung=$1; shift
  if "$@" >/dev/null 2>&1; then
    gruen "  PASS  $beschreibung"; PASS=$((PASS + 1))
  else
    rot "  FAIL  $beschreibung"; FAIL=$((FAIL + 1))
  fi
}

warte_auf() { # warte_auf "Beschreibung" <sekunden> <befehl...>  (0 = bedingung erfuellt)
  local beschreibung=$1 zeitlimit=$2; shift 2
  local start=$SECONDS
  while ! "$@" >/dev/null 2>&1; do
    if (( SECONDS - start >= zeitlimit )); then
      rot "  TIMEOUT nach ${zeitlimit}s: $beschreibung"
      return 1
    fi
    sleep 2
  done
  return 0
}

uuid() { cat /proc/sys/kernel/random/uuid; }

# Annahme: uuid id kaeufer lieferplanet [traceparent] -> HTTP-Status-Code
post_auftrag() {
  local id=$1 kaeufer=$2 planet=$3 traceparent=${4:-}
  local args=(-sS -o /dev/null -w '%{http_code}' -X POST "$SERVICE/api/v1/kaufauftraege"
    -H 'Content-Type: application/json' -H "Idempotency-Key: $id")
  if [[ -n "$traceparent" ]]; then args+=(-H "traceparent: $traceparent"); fi
  curl "${args[@]}" -d "{\"kaeufer\": \"$kaeufer\", \"klasse\": \"IMPERIAL_I\", \"anzahl\": 2, \"lieferplanet\": $planet}"
}

# Feld aus dem Auftragsstand: stand_feld <id> <json-pfad-mit-jq>
stand_feld() {
  curl -sS "$SERVICE/api/v1/kaufauftraege/$1" | jq -r "$2"
}

# Fuer Assertions/Polls in Sub-Shells (bash -c) verfuegbar machen
export -f post_auftrag stand_feld

dienst_laeuft() { curl -sS -o /dev/null --max-time 2 "$1"; }

aufraeumen() {
  if [[ -n "$SERVICE_PID" ]] && kill -0 "$SERVICE_PID" 2>/dev/null; then
    gelb "Stoppe Service (PID $SERVICE_PID)"
    kill "$SERVICE_PID" 2>/dev/null || true
  fi
}
trap aufraeumen EXIT

# ---------------------------------------------------------------- 1. Vorpruefung

gelb "== Vorpruefung der devenv-Dienste =="
pruefe "Postgres erreichbar (5432)" bash -c 'echo > /dev/tcp/127.0.0.1/5432'
pruefe "WireMock erreichbar (8089)" dienst_laeuft "$WIREMOCK/__admin/health"
pruefe "OpenSearch erreichbar (9200)" dienst_laeuft "$OPENSEARCH"
pruefe "Kafka erreichbar (9092)" bash -c 'echo > /dev/tcp/127.0.0.1/9092'
if [[ $FAIL -gt 0 ]]; then
  rot "Dienste fehlen: bitte 'devenv up -d' ausfuehren."
  exit 1
fi

curl -sS -X POST "$WIREMOCK/__admin/scenarios/reset" -o /dev/null

# ---------------------------------------------------------------- 2. Service starten

if [[ ! -f "$RUNNER" ]]; then
  gelb "Runner fehlt, baue (mvn package -DskipTests) ..."
  (cd raumschiffwerft-service && mvn -q -am package -DskipTests) || { rot "Build gescheitert"; exit 1; }
fi

gelb "== Starte Service mit kurzen Test-Intervallen =="
java -Dzustellung.wiederholung-sekunden=1 \
     -Dzustellung.lease-sekunden=2 \
     -Dabgleich.timer-period-millis=2000 \
     -Dabgleich.backoff-max-sekunden=4 \
     -Dabgleich.max-versuche=3 \
     -jar "$RUNNER" > /tmp/raumschiffwerft-e2e.log 2>&1 &
SERVICE_PID=$!
if ! warte_auf "Service antwortet auf 8080" 60 dienst_laeuft "$SERVICE/api/v1/kaufauftraege/$(uuid)"; then
  rot "Service startet nicht, siehe /tmp/raumschiffwerft-e2e.log"
  exit 1
fi
gruen "Service laeuft (Log: /tmp/raumschiffwerft-e2e.log)"

# ---------------------------------------------------------------- 3. Szenarien

gelb "== Szenario 1: Erfolgreiche Imperium-Zustellung =="
IMPERIUM_ID=$(uuid)
pruefe "POST Tarkin/42 liefert 200" test "$(post_auftrag "$IMPERIUM_ID" 'Tarkin' 42)" = "200"
pruefe "Zustellung BESTAETIGT mit ISD-4711" \
  bash -c "[ \"\$(curl -sS $SERVICE/api/v1/kaufauftraege/$IMPERIUM_ID | jq -r '.zustellungen[0].status')\" = BESTAETIGT ] && [ \"\$(curl -sS $SERVICE/api/v1/kaufauftraege/$IMPERIUM_ID | jq -r '.zustellungen[0].externeReferenz')\" = ISD-4711 ]"

gelb "== Szenario 2: Rebellion per lieferplanet 4 (Yavin 4) =="
REBELLION_ID=$(uuid)
pruefe "POST Mon Mothma/4 liefert 200" test "$(post_auftrag "$REBELLION_ID" 'Mon Mothma' 4)" = "200"
pruefe "Zustellung BESTAETIGT mit RB-1138 an der Rebellion" \
  bash -c "[ \"\$(curl -sS $SERVICE/api/v1/kaufauftraege/$REBELLION_ID | jq -r '.zustellungen[0].zielsystem')\" = REBELLION ] && [ \"\$(curl -sS $SERVICE/api/v1/kaufauftraege/$REBELLION_ID | jq -r '.zustellungen[0].externeReferenz')\" = RB-1138 ]"

gelb "== Szenario 3: Idempotenz - derselbe Key liefert denselben Stand =="
pruefe "Wiederholter POST liefert 200 und versuche bleibt 1" \
  bash -c "[ \"\$(post_auftrag "$IMPERIUM_ID" 'Tarkin' 42)\" = 200 ] && [ \"\$(stand_feld "$IMPERIUM_ID" '.zustellungen[0].versuche')\" = 1 ]"

gelb "== Szenario 4: Fehlerfall konvergiert ueber den Abgleich zu BESTAETIGT =="
JARJAR_ID=$(uuid)
STATUS=$(post_auftrag "$JARJAR_ID" 'Jar Jar Binks' 42)
pruefe "POST Jar Jar liefert 202 (UNGEKLAERT)" test "$STATUS" = "202"
if warte_auf "Jar-Jar-Auftrag erreicht BESTAETIGT" 90 \
     bash -c "[ \"\$(stand_feld "$JARJAR_ID" '.zustellungen[0].status')\" = BESTAETIGT ]"; then
  PASS=$((PASS + 1)); gruen "  PASS  Abgleich hat die Zustellung nachgeholt"
else
  FAIL=$((FAIL + 1)); rot "  FAIL  Abgleich hat die Zustellung nicht nachgeholt"
fi

gelb "== Szenario 5: Timeout konvergiert ebenfalls zu BESTAETIGT =="
LANGSAM_ID=$(uuid)
STATUS=$(post_auftrag "$LANGSAM_ID" 'Langsam Kaufmann' 42)
pruefe "POST Langsam liefert 202 (Timeout nach 2s, UNGEKLAERT)" test "$STATUS" = "202"
if warte_auf "Langsam-Auftrag erreicht BESTAETIGT" 90 \
     bash -c "[ \"\$(stand_feld "$LANGSAM_ID" '.zustellungen[0].status')\" = BESTAETIGT ]"; then
  PASS=$((PASS + 1)); gruen "  PASS  Abgleich hat die Timeout-Zustellung nachgeholt"
else
  FAIL=$((FAIL + 1)); rot "  FAIL  Timeout-Zustellung konvergiert nicht"
fi

gelb "== Szenario 6: Journaleintrag mit Trace-ID erscheint in OpenSearch =="
TRACE_ID="0af7651916cd43dd8448eb4c9cbbfda1"
JOURNAL_ID=$(uuid)
post_auftrag "$JOURNAL_ID" 'Tarkin' 42 "00-$TRACE_ID-0af7651916cd43dd-01" >/dev/null
if warte_auf "OpenSearch-Dokument mit traceId $TRACE_ID" 60 \
     bash -c "[ \"\$(curl -sS $OPENSEARCH/durchlauferhitzer-journal/_doc/$JOURNAL_ID | jq -r '._source.traceId')\" = $TRACE_ID ]"; then
  PASS=$((PASS + 1)); gruen "  PASS  Journal-Kette Annahme → Kafka → OpenSearch funktioniert"
else
  FAIL=$((FAIL + 1)); rot "  FAIL  Journaleintrag erscheint nicht in OpenSearch"
fi

gelb "== Szenario 7: Fehlersemantik des REST-Vertrags =="
pruefe "POST ohne Idempotency-Key liefert 400" \
  test "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$SERVICE/api/v1/kaufauftraege" -H 'Content-Type: application/json' -d '{"kaeufer": "Tarkin", "klasse": "IMPERIAL_I", "anzahl": 2, "lieferplanet": 42}')" = "400"
pruefe "POST mit ungueltigem Body (lieferplanet 99) liefert 400" \
  test "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$SERVICE/api/v1/kaufauftraege" -H "Idempotency-Key: $(uuid)" -H 'Content-Type: application/json' -d '{"kaeufer": "Tarkin", "klasse": "IMPERIAL_I", "anzahl": 2, "lieferplanet": 99}')" = "400"
pruefe "GET unbekannter Auftrag liefert 404" \
  test "$(curl -sS -o /dev/null -w '%{http_code}' "$SERVICE/api/v1/kaufauftraege/$(uuid)")" = "404"

# ---------------------------------------------------------------- 4. Zusammenfassung

echo ""
if [[ $FAIL -eq 0 ]]; then
  gruen "== E2E bestanden: $PASS/$((PASS + FAIL)) Pruefungen gruen =="
else
  rot "== E2E mit Fehlern: $PASS bestanden, $FAIL fehlgeschlagen (Service-Log: /tmp/raumschiffwerft-e2e.log) =="
fi
exit $((FAIL > 0))
