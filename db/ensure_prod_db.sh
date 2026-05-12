#!/usr/bin/env bash
set -euo pipefail

# Konfiguration
LABEL="vm_prod"
IMAGE="postgres:18"
DB_NAME="konfplan"
CONTAINER_NAME="vortragsmanager_prod"
DB_PORT=5432

# Passwort (bevorzugt aus Umgebungsvariable, sonst Default aus application.properties)
DB_PASSWORD="${DB_PASSWORD:-vm4HjK$}"

echo "🔍 Suche nach Produktion-Container mit Label '$LABEL'..."

# Prüfe, ob ein Container mit diesem Label existiert (unabhängig vom Status)
CONTAINER_ID=$(docker ps -a --filter "label=$LABEL" --format "{{.ID}}")

if [[ -z "$CONTAINER_ID" ]]; then
    echo "✨ Kein passender Container gefunden. Erstelle neuen Container '$CONTAINER_NAME'..."
    
    # Erstellt den Container und legt die DB 'konfplan' automatisch an
    docker run -d \
        --name "$CONTAINER_NAME" \
        --label "$LABEL" \
        -e POSTGRES_DB="$DB_NAME" \
        -e POSTGRES_PASSWORD="$DB_PASSWORD" \
        -p "$DB_PORT":5432 \
        "$IMAGE"
    
    echo "✅ Container wurde erstellt und gestartet."
else
    # Container existiert, prüfe ob er läuft
    STATUS=$(docker inspect -f '{{.State.Running}}' "$CONTAINER_ID")
    
    if [[ "$STATUS" == "true" ]]; then
        echo "✅ Container läuft bereits (ID: $CONTAINER_ID)."
    else
        echo "⏳ Container existiert, ist aber gestoppt. Starte..."
        docker start "$CONTAINER_ID"
        echo "✅ Container wurde gestartet."
    fi
fi

# Warte kurz, bis Postgres wirklich bereit ist, Verbindungen anzunehmen
echo "🕒 Warte auf Datenbank-Bereitschaft..."
until docker exec "$CONTAINER_NAME" pg_isready -U postgres > /dev/null 2>&1; do
    sleep 1
done

echo "🐘 PostgreSQL ist bereit unter Port $DB_PORT."
echo "   DB: $DB_NAME"
echo "   Label: $LABEL"