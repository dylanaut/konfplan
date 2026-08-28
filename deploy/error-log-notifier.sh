#!/bin/sh
# Tailt laufend die Logs des "app"-Containers und schickt jede Zeile mit "ERROR" per ntfy.sh als
# Push-Benachrichtigung raus - Echtzeit-Alarm zusaetzlich zu Prometheus/Grafana (siehe
# Monitoring-Grafana.adoc), das im Stack keinen Alertmanager hat und daher selbst nichts meldet.
#
# Laeuft als eigener Sidecar-Container (Profil "alerting", siehe docker-compose.yml), nicht auf
# dem Host - deshalb kein Cron wie bei check-outbound-ip.sh, sondern eine Dauerschleife.
set -eu

if [ -z "${NTFY_ERROR_TOPIC:-}" ]; then
  echo "$(date -Iseconds) NTFY_ERROR_TOPIC nicht gesetzt (siehe deploy/.env) - Watcher deaktiviert." >&2
  exec sleep infinity
fi

command -v curl >/dev/null 2>&1 || apk add --no-cache curl >/dev/null

MIN_INTERVAL_SECONDS=30
LAST_SENT=0

notify() {
  line="$1"
  now="$(date +%s)"
  if [ $((now - LAST_SENT)) -lt "$MIN_INTERVAL_SECONDS" ]; then
    echo "$(date -Iseconds) ERROR unterdrueckt (Mindestabstand ${MIN_INTERVAL_SECONDS}s, um ntfy/Handy bei einer Fehlerserie nicht zu fluten): $line" >&2
    return
  fi
  LAST_SENT="$now"
  curl -fsS \
    -H "Title: KonfPlan: ERROR im App-Log" \
    -H "Priority: high" \
    -d "$line" \
    "https://ntfy.sh/$NTFY_ERROR_TOPIC" > /dev/null \
    || echo "$(date -Iseconds) ntfy-Benachrichtigung fehlgeschlagen." >&2
}

find_app_container() {
  docker ps --filter "label=com.docker.compose.service=app" --format '{{.Names}}' | head -n1
}

while true; do
  CONTAINER="$(find_app_container)"
  if [ -z "$CONTAINER" ]; then
    sleep 2
    continue
  fi

  # "--tail 0": nur neue Zeilen ab jetzt - beim (Neu-)Verbinden nach einem App-Neustart soll nicht
  # der komplette bisherige Log erneut nach ERROR durchsucht werden.
  docker logs -f --tail 0 "$CONTAINER" 2>&1 | while IFS= read -r line; do
    case "$line" in
      *ERROR*) notify "$line" ;;
    esac
  done

  echo "$(date -Iseconds) docker logs-Stream beendet (Container neu gestartet?) - verbinde neu." >&2
  sleep 2
done
