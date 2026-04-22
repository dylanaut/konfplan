#!/usr/bin/env bash
set -euo pipefail

# 'quarkus.http.port' in application.properties
QUARKUS_DEV_PORT=9000

PROJECT_DIR=~/Java/berufsorientierung/vortragsmanager
DB_SCHEMA=vortragsmanager
SCRIPT_DIR=$PROJECT_DIR/backend/src/main/resources/db/migration
ENV="${1:-}"  # erstes Argument, optional

# --- Port ermitteln ---
get_quarkus_dev_port2() {
    local port
    port=$(docker ps \
        --filter "label=io.quarkus.devservice" \
        --format "{{.Ports}}" \
        | sed 's/.*:\([0-9]*\)->5432.*/\1/' \
        | head -1)

    if [[ -z "$port" ]]; then
        echo "❌ Kein Quarkus Dev Service Container gefunden!" >&2
        echo "   Läuft 'quarkus dev'?" >&2
        exit 1
    fi
    echo "$port"
}

# --- Umgebung konfigurieren ---
if [[ "$ENV" == "PROD" ]]; then
    DB_HOST="${DB_HOST:-prod-server}"
    DB_PORT=5432
    DB_NAME="${DB_NAME:-meinedb}"
    DB_USER="${DB_USER:-postgres}"
    echo "🚀 Modus: PRODUKTION ($DB_HOST:$DB_PORT)"
else
    DB_HOST="${DB_HOST:-localhost}"
    DB_PORT=$(get_quarkus_dev_port)
    DB_NAME="${DB_NAME:-${DB_SCHEMA}}"
    DB_USER="${DB_USER:-quarkus}"
    echo "🛠  Modus: ENTWICKLUNG (Quarkus Dev Port: $DB_PORT)"
fi

export PGPASSWORD="${DB_PASSWORD:-quarkus}"

# --- Hilfsfunktion ---
run_sql() {
    local file="$1"
    echo "  → $(basename "$file")"
    psql \
        --host="$DB_HOST" \
        --port="$DB_PORT" \
        --username="$DB_USER" \
        --dbname="$DB_NAME" \
        --variable=SCHEMA="${DB_SCHEMA:-mein_schema}" \
        --file="$file" \
        --on-error-stop
}

# --- Ausführen ---
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "CREATE SCHEMA IF NOT EXISTS $DB_SCHEMA;"

run_sql "$SCRIPT_DIR/V1__tables.sql"
run_sql "$SCRIPT_DIR/V2__data.sql"

echo "✅ Setup abgeschlossen"

