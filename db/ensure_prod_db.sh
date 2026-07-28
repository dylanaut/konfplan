#!/usr/bin/env bash
set -euo pipefail

# Konfiguration
LABEL="vm_prod"
IMAGE="mcr.microsoft.com/azure-sql-edge:latest"
DB_NAME="konfplan"
CONTAINER_NAME="konfplan_prod"
DB_PORT=1433

# HINWEIS: mcr.microsoft.com/azure-sql-edge wurde von Microsoft zum 30.09.2025 offiziell
# abgekuendigt (keine weiteren Updates/Patches). Es ist aktuell die einzige lokal auf ARM64
# (Apple Silicon) lauffaehige SQL-Server-kompatible Option und wird deshalb bewusst weiter
# verwendet. sqlcmd ist in diesem Image NICHT enthalten - isql (unixODBC, mit dem im Image
# bereits vorhandenen "ODBC Driver 17 for SQL Server") ist der funktionierende Ersatz.

# Passwort (bevorzugt aus Umgebungsvariable, sonst Default). Muss SQL-Server-Passwortrichtlinie
# erfuellen: mind. 8 Zeichen aus 3 von 4 Zeichenklassen (Gross/Klein/Zahl/Sonderzeichen).
DB_PASSWORD="${DB_PASSWORD:-vm4HjK\$26}"

isql_query() {
    docker exec "$CONTAINER_NAME" sh -c \
        "echo \"$1\" | isql -k 'Driver={ODBC Driver 17 for SQL Server};Server=localhost;Uid=sa;Pwd=${DB_PASSWORD};Encrypt=no;'"
}

echo "🔍 Suche nach Produktion-Container mit Label '$LABEL'..."

# Prüfe, ob ein Container mit diesem Label existiert (unabhängig vom Status)
CONTAINER_ID=$(docker ps -a --filter "label=$LABEL" --format "{{.ID}}")

if [[ -z "$CONTAINER_ID" ]]; then
    echo "✨ Kein passender Container gefunden. Erstelle neuen Container '$CONTAINER_NAME'..."

    docker run -d \
        --name "$CONTAINER_NAME" \
        --label "$LABEL" \
        -e ACCEPT_EULA=Y \
        -e MSSQL_SA_PASSWORD="$DB_PASSWORD" \
        -p "$DB_PORT":1433 \
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

# Warte kurz, bis SQL Server wirklich bereit ist, Verbindungen anzunehmen
echo "🕒 Warte auf Datenbank-Bereitschaft..."
until isql_query "SELECT 1;" > /dev/null 2>&1; do
    sleep 1
done

# Anders als POSTGRES_DB bei Postgres legt Azure SQL Edge keine benannte Datenbank automatisch
# an - einmaliger, idempotenter Schritt.
echo "🗄️  Stelle sicher, dass Datenbank '$DB_NAME' existiert..."
isql_query "IF DB_ID('$DB_NAME') IS NULL CREATE DATABASE $DB_NAME;" > /dev/null

echo "🗄️  SQL Server ist bereit unter Port $DB_PORT."
echo "   DB: $DB_NAME"
echo "   Label: $LABEL"
