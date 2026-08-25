#!/bin/sh
# Prueft die aktuelle ausgehende IP-Adresse dieses Servers und schickt bei einer Aenderung eine
# ntfy.sh-Push-Benachrichtigung - unabhaengig von Brevo/SMTP, damit die Meldung auch dann
# ankommt, wenn genau eine IP-Aenderung gerade den Mailversand blockiert (siehe
# Deployment-DockerCompose.adoc, Abschnitt "Ausgehende Server-IP ueberwachen").
#
# Aufruf z.B. per Cron: */30 * * * * /pfad/zu/deploy/check-outbound-ip.sh >> /var/log/konfplan-ip-check.log 2>&1
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="$SCRIPT_DIR/ip-monitor-state.txt"
ENV_FILE="$SCRIPT_DIR/.env"

if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  . "$ENV_FILE"
fi

if [ -z "${NTFY_TOPIC:-}" ]; then
  echo "$(date -Iseconds) NTFY_TOPIC nicht gesetzt (siehe deploy/.env) - Abbruch." >&2
  exit 1
fi

CURRENT_IP="$(curl -fsS --max-time 10 https://api.ipify.org)"

if [ -z "$CURRENT_IP" ]; then
  echo "$(date -Iseconds) Konnte aktuelle ausgehende IP nicht ermitteln." >&2
  exit 1
fi

if [ ! -f "$STATE_FILE" ]; then
  echo "$CURRENT_IP" > "$STATE_FILE"
  echo "$(date -Iseconds) Erststart: aktuelle IP $CURRENT_IP als Ausgangswert gespeichert."
  exit 0
fi

LAST_IP="$(cat "$STATE_FILE")"

if [ "$CURRENT_IP" != "$LAST_IP" ]; then
  curl -fsS \
    -H "Title: KonfPlan: Server-IP geändert" \
    -H "Priority: high" \
    -d "Ausgehende IP hat sich von $LAST_IP auf $CURRENT_IP geändert. In Brevo unter SMTP & API -> Authorized IPs ergänzen: https://app.brevo.com" \
    "https://ntfy.sh/$NTFY_TOPIC" > /dev/null
  echo "$CURRENT_IP" > "$STATE_FILE"
  echo "$(date -Iseconds) IP-Aenderung erkannt und gemeldet: $LAST_IP -> $CURRENT_IP"
else
  echo "$(date -Iseconds) IP unveraendert: $CURRENT_IP"
fi
