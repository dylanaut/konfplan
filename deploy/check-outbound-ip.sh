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
  BREVO_UPDATE_STATUS="nicht versucht (BREVO_LOGIN_EMAIL nicht gesetzt)"
  if [ -n "${BREVO_LOGIN_EMAIL:-}" ]; then
    BREVO_PASSWORD_FILE="$SCRIPT_DIR/secrets/brevo_login_password.txt"
    BREVO_UPDATER_LOG="$SCRIPT_DIR/brevo-ip-updater/last-run.log"
    if [ -f "$BREVO_PASSWORD_FILE" ]; then
      # Ausgabe zusaetzlich in eine feste Datei im gemounteten Volume schreiben - unabhaengig
      # davon, ob/wohin der aeussere Cron-Aufruf selbst umgeleitet wird, ist so nach jedem Lauf
      # (Erfolg oder Fehlschlag) die Playwright-/Node-Ausgabe an einem festen Ort nachvollziehbar.
      set +e
      docker run --rm \
        -e BREVO_LOGIN_EMAIL="$BREVO_LOGIN_EMAIL" \
        -e BREVO_LOGIN_PASSWORD="$(cat "$BREVO_PASSWORD_FILE")" \
        -v "$SCRIPT_DIR/brevo-ip-updater:/work" -w /work \
        mcr.microsoft.com/playwright:v1.62.1-noble \
        node update-ip.mjs "$CURRENT_IP" > "$BREVO_UPDATER_LOG" 2>&1
      BREVO_EXIT_CODE=$?
      set -e
      cat "$BREVO_UPDATER_LOG"
      if [ "$BREVO_EXIT_CODE" -eq 0 ]; then
        BREVO_UPDATE_STATUS="automatisch erfolgreich eingetragen"
      else
        BREVO_UPDATE_STATUS="automatische Eintragung FEHLGESCHLAGEN (Log: $BREVO_UPDATER_LOG) - manuell nachtragen: https://app.brevo.com/security/authorised_ips"
      fi
    fi
  fi

  curl -fsS \
    -H "Title: KonfPlan: Server-IP geändert" \
    -H "Priority: high" \
    -d "Ausgehende IP hat sich von $LAST_IP auf $CURRENT_IP geändert. Brevo-Autorisierung: $BREVO_UPDATE_STATUS" \
    "https://ntfy.sh/$NTFY_TOPIC" > /dev/null
  echo "$CURRENT_IP" > "$STATE_FILE"
  echo "$(date -Iseconds) IP-Aenderung erkannt und gemeldet: $LAST_IP -> $CURRENT_IP (Brevo: $BREVO_UPDATE_STATUS)"
else
  echo "$(date -Iseconds) IP unveraendert: $CURRENT_IP"
fi
