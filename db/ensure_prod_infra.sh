#!/usr/bin/env bash
set -euo pipefail

# Startet die für einen lokalen PROD-Testlauf (z.B. Native-Image-Runner) benötigte
# Infrastruktur: MiniZinc (lokale Installation, kein Docker-Image verfügbar),
# PostgreSQL (via ensure_prod_db.sh), Keycloak (Authentifizierung) + Mailpit
# (statt Brevo-SMTP).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Muss mit dem (ggf. per Umgebungsvariable ueberschriebenen) Passwort in
# ensure_prod_db.sh uebereinstimmen - dort nicht exportiert, da als separates
# Skript aufgerufen, nicht ge-source-t.
DB_PASSWORD="${DB_PASSWORD:-vm4HjK\$26}"

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

# --- PostgreSQL ---
"$SCRIPT_DIR/ensure_prod_db.sh"

# --- Keycloak ---
# Nutzt die gleiche Postgres-Instanz wie oben (eigene Datenbank 'keycloak'),
# und die zentrale Realm-Vorlage aus deploy/ (gleiche Quelle wie Docker-Compose-
# und .deb-Verfahren) statt einer eigenen Kopie.
KEYCLOAK_LABEL="vm_prod_keycloak"
KEYCLOAK_IMAGE="quay.io/keycloak/keycloak:26.5"
KEYCLOAK_CONTAINER_NAME="konfplan_prod_keycloak"
KEYCLOAK_PORT="${KEYCLOAK_PORT:-8080}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin}"
KC_ADMIN_CLI_SECRET="${KC_ADMIN_CLI_SECRET:-local-native-test-secret}"
APP_PUBLIC_URL="${APP_PUBLIC_URL:-http://localhost:9000}"

echo "🔍 Prüfe 'keycloak'-Datenbank in PostgreSQL..."
if ! docker exec konfplan_prod psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='keycloak'" | grep -q 1; then
    docker exec konfplan_prod psql -U postgres -c "CREATE DATABASE keycloak" > /dev/null
    echo "✅ Datenbank 'keycloak' angelegt."
else
    echo "✅ Datenbank 'keycloak' existiert bereits."
fi

echo "🧩 Rendere Realm-Import aus deploy/keycloak-realm.template.json..."
REALM_TEMPLATE="$SCRIPT_DIR/../deploy/keycloak-realm.template.json"
REALM_GENERATED="$SCRIPT_DIR/../deploy/keycloak-realm.generated.json"
sed \
    -e "s|__ADMIN_CLI_SECRET__|${KC_ADMIN_CLI_SECRET}|g" \
    -e "s|__APP_PUBLIC_URL__|${APP_PUBLIC_URL}|g" \
    "$REALM_TEMPLATE" > "$REALM_GENERATED"
chmod 600 "$REALM_GENERATED"

echo "🔍 Suche nach Keycloak-Container mit Label '$KEYCLOAK_LABEL'..."
CONTAINER_ID=$(docker ps -a --filter "label=$KEYCLOAK_LABEL" --format "{{.ID}}")

if [[ -z "$CONTAINER_ID" ]]; then
    echo "✨ Kein passender Container gefunden. Erstelle neuen Container '$KEYCLOAK_CONTAINER_NAME'..."

    # host.docker.internal erreicht die per '-p 5432:5432' auf dem Host
    # exponierte PostgreSQL aus dem Container heraus - funktioniert so direkt
    # unter Docker Desktop (macOS), --add-host macht es zusaetzlich auf
    # nativem Linux-Docker verfuegbar.
    docker run -d \
        --name "$KEYCLOAK_CONTAINER_NAME" \
        --label "$KEYCLOAK_LABEL" \
        --add-host=host.docker.internal:host-gateway \
        -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
        -e KC_BOOTSTRAP_ADMIN_PASSWORD="$KC_ADMIN_PASSWORD" \
        -e KC_DB=postgres \
        -e KC_DB_URL_HOST=host.docker.internal \
        -e KC_DB_URL_DATABASE=keycloak \
        -e KC_DB_USERNAME=postgres \
        -e KC_DB_PASSWORD="$DB_PASSWORD" \
        -e KC_HTTP_PORT=8080 \
        -e KC_HOSTNAME=localhost \
        -e KC_HOSTNAME_STRICT=false \
        -e KC_HTTP_ENABLED=true \
        -p "$KEYCLOAK_PORT":8080 \
        -v "$REALM_GENERATED":/opt/keycloak/data/import/konfplan-realm.json:ro \
        "$KEYCLOAK_IMAGE" start --import-realm

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

echo "🕒 Warte auf Keycloak-Bereitschaft..."
until curl -sf "http://localhost:$KEYCLOAK_PORT/realms/konfplan" > /dev/null 2>&1; do
    sleep 1
done

echo "🔐 Keycloak ist bereit unter http://localhost:$KEYCLOAK_PORT (Realm 'konfplan')."

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
cat <<EOF

export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=konfplan
export DB_USER=postgres
export DB_PASSWORD='$DB_PASSWORD'
export KC_SERVER_URL=http://localhost:$KEYCLOAK_PORT
export KC_REALM=konfplan
export KC_ADMIN_CLI_SECRET=$KC_ADMIN_CLI_SECRET
export QUARKUS_MAILER_HOST=localhost
export QUARKUS_MAILER_PORT=1025
export QUARKUS_MAILER_START_TLS=DISABLED
export QUARKUS_MAILER_USERNAME=test
export QUARKUS_MAILER_PASSWORD=test
./backend/target/konfplan-backend-1.0.0-SNAPSHOT-runner

EOF
