#!/usr/bin/env bash
set -euo pipefail

# Startet die für einen lokalen PROD-Testlauf (z.B. Native-Image-Runner) benötigte
# Infrastruktur: MiniZinc (lokale Installation, kein Docker-Image verfügbar),
# SQL Server/Azure SQL Edge (via ensure_prod_db.sh) + Mailpit (statt Brevo-SMTP).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- MiniZinc ---
# Wird von PlanErstellungService als externer Prozess über den absoluten Pfad
# aus 'minizinc.path' (application.properties) aufgerufen, nicht über PATH-Suche.
MINIZINC_PATH="${MINIZINC_PATH:-/opt/homebrew/bin/minizinc}"

echo "🔍 Prüfe MiniZinc-Installation ('$MINIZINC_PATH')..."
if [[ -x "$MINIZINC_PATH" ]]; then
    echo "✅ MiniZinc gefunden: $("$MINIZINC_PATH" --version | head -1)"
elif command -v minizinc > /dev/null 2>&1; then
    FOUND_PATH=$(command -v minizinc)
    echo "⚠️  MiniZinc nicht unter '$MINIZINC_PATH', aber im PATH unter '$FOUND_PATH' gefunden."
    echo "   Vor dem Start: export MINIZINC_PATH=$FOUND_PATH (oder 'minizinc.path' in application.properties anpassen)."
    exit 1
else
    echo "❌ MiniZinc wurde nicht gefunden (weder unter '$MINIZINC_PATH' noch im PATH)."
    echo "   MiniZinc wird für die automatische Planerstellung zur Laufzeit benötigt und muss lokal installiert werden:"
    case "$(uname -s)" in
        Darwin) echo "   macOS:  brew install minizinc" ;;
        Linux)  echo "   Linux:  sudo snap install minizinc --classic  (oder offizielles Bundle installieren)" ;;
        *)      echo "   siehe offizielle MiniZinc-Installationsanleitung für dein Betriebssystem." ;;
    esac
    echo "   Weicht der Installationspfad von '$MINIZINC_PATH' ab: vorher MINIZINC_PATH entsprechend exportieren."
    exit 1
fi

# --- SQL Server (Azure SQL Edge) ---
"$SCRIPT_DIR/ensure_prod_db.sh"

# --- Mailpit ---
MAILPIT_LABEL="vm_prod_mailpit"
MAILPIT_IMAGE="axllent/mailpit:latest"
MAILPIT_CONTAINER_NAME="konfplan_prod_mailpit"
MAILPIT_SMTP_PORT=1025
MAILPIT_UI_PORT=8025

echo "🔍 Suche nach Mailpit-Container mit Label '$MAILPIT_LABEL'..."
CONTAINER_ID=$(docker ps -a --filter "label=$MAILPIT_LABEL" --format "{{.ID}}")

if [[ -z "$CONTAINER_ID" ]]; then
    echo "✨ Kein passender Container gefunden. Erstelle neuen Container '$MAILPIT_CONTAINER_NAME'..."

    docker run -d \
        --name "$MAILPIT_CONTAINER_NAME" \
        --label "$MAILPIT_LABEL" \
        -p "$MAILPIT_SMTP_PORT":1025 \
        -p "$MAILPIT_UI_PORT":8025 \
        "$MAILPIT_IMAGE"

    echo "✅ Container wurde erstellt und gestartet."
else
    STATUS=$(docker inspect -f '{{.State.Running}}' "$CONTAINER_ID")

    if [[ "$STATUS" == "true" ]]; then
        echo "✅ Container läuft bereits (ID: $CONTAINER_ID)."
    else
        echo "⏳ Container existiert, ist aber gestoppt. Starte..."
        docker start "$CONTAINER_ID"
        echo "✅ Container wurde gestartet."
    fi
fi

echo "🕒 Warte auf Mailpit-Bereitschaft..."
until curl -sf "http://localhost:$MAILPIT_UI_PORT/" > /dev/null 2>&1; do
    sleep 1
done

echo "📬 Mailpit ist bereit."
echo "   SMTP:   localhost:$MAILPIT_SMTP_PORT"
echo "   Web UI: http://localhost:$MAILPIT_UI_PORT"

echo ""
echo "✅ Infrastruktur bereit. Native-Runner z.B. so starten:"
cat <<'EOF'

export DB_HOST=localhost
export DB_PORT=1433
export DB_NAME=konfplan
export DB_USER=sa
export DB_PASSWORD='vm4HjK$26'
export QUARKUS_MAILER_HOST=localhost
export QUARKUS_MAILER_PORT=1025
export QUARKUS_MAILER_START_TLS=DISABLED
export QUARKUS_MAILER_USERNAME=test
export QUARKUS_MAILER_PASSWORD=test
./backend/target/konfplan-backend-1.0.0-SNAPSHOT-runner

EOF
