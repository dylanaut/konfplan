#!/usr/bin/env bash
set -euo pipefail

# Bereitet ein frisches (oder bereits laufendes) Deployment vor:
#   1. Generiert fehlende Passwoerter direkt auf diesem Host in secrets/*.txt (chmod 600) -
#      nichts wird jemals uebertragen, im Repo committed oder in .env/docker-compose.yml
#      hinterlegt. Bereits vorhandene Secret-Dateien werden NICHT ueberschrieben, damit ein
#      erneuter Aufruf (z.B. bei einem Redeploy) keine Passwoerter rotiert.
#   2. Rendert keycloak-realm.template.json -> keycloak-realm.generated.json mit dem
#      generierten Admin-CLI-Secret und der konfigurierten App-URL.
#
# Idempotent - beliebig oft erneut ausfuehrbar, z.B. vor jedem "docker compose up -d".

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -f .env ]]; then
    echo "❌ .env fehlt. Erst 'cp .env.example .env' ausfuehren und anpassen."
    exit 1
fi

if ! command -v openssl > /dev/null 2>&1; then
    echo "❌ openssl wird zur Passwort-Generierung benoetigt, ist aber nicht installiert."
    exit 1
fi

mkdir -p secrets
chmod 700 secrets

generate_secret_if_missing() {
    local name="$1"
    local file="secrets/${name}.txt"
    if [[ -f "$file" ]]; then
        echo "✅ secrets/${name}.txt existiert bereits - unveraendert."
    else
        echo "✨ Generiere secrets/${name}.txt..."
        # Hex statt Base64: Base64 kann '+', '/', '=' enthalten, die in anderen Kontexten (URL-
        # Query-Parameter, form-urlencoded Requests, YAML) erst kodiert werden muessten - Hex
        # (nur [0-9a-f]) ist ueberall ohne Sonderfaelle sicher.
        openssl rand -hex 32 > "$file"
        chmod 600 "$file"
    fi
}

echo "🔐 Pruefe/generiere Secrets..."
generate_secret_if_missing db_password
generate_secret_if_missing keycloak_admin_password
generate_secret_if_missing keycloak_admin_cli_secret
# Wird nur gebraucht, wenn kein Profil "mailpit" genutzt wird (echter SMTP-Versand) - trotzdem
# immer generieren, da docker-compose sonst die fehlende Secret-Datei als Fehler behandelt.
generate_secret_if_missing brevo_smtp_password

# --- Realm-Template rendern ---
echo "🧩 Rendere keycloak-realm.generated.json..."
# shellcheck disable=SC1091
set -a; source .env; set +a
ADMIN_CLI_SECRET="$(cat secrets/keycloak_admin_cli_secret.txt)"
APP_URL="${APP_PUBLIC_URL:-http://localhost:9000}"

sed \
    -e "s|__ADMIN_CLI_SECRET__|${ADMIN_CLI_SECRET}|g" \
    -e "s|__APP_PUBLIC_URL__|${APP_URL}|g" \
    keycloak-realm.template.json > keycloak-realm.generated.json
chmod 600 keycloak-realm.generated.json

# --- Pflichtvariablen fuer Profil "public" (Caddy/TLS) ---
# Bewusst hier statt per Compose "${VAR:?...}" geprueft: Compose wertet solche Pflicht-Variablen
# beim Parsen fuer JEDEN Service aus, auch fuer inaktive Profile - das wuerde auch den
# lokalen/UTM-Fall ohne Caddy brechen.
if [[ "${1:-}" == "public" ]]; then
    missing=()
    [[ -z "${APP_HOSTNAME:-}" ]] && missing+=(APP_HOSTNAME)
    [[ -z "${KEYCLOAK_HOSTNAME:-}" ]] && missing+=(KEYCLOAK_HOSTNAME)
    [[ -z "${ACME_EMAIL:-}" ]] && missing+=(ACME_EMAIL)
    if [[ ${#missing[@]} -gt 0 ]]; then
        echo "❌ Profil 'public' braucht folgende Variablen in .env: ${missing[*]}"
        exit 1
    fi
fi

echo "✅ Bereit. Beispiele:"
echo "   Lokal/UTM:        docker compose up -d"
echo "   Mit Test-Mailer:  docker compose --profile mailpit up -d"
echo "   Hetzner (public): ./bootstrap.sh public && docker compose --profile public up -d"
