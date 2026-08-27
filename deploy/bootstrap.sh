#!/usr/bin/env bash
set -euo pipefail

# Bereitet ein frisches (oder bereits laufendes) Deployment vor:
#   1. Generiert fehlende Passwoerter direkt auf diesem Host in secrets/*.txt (chmod 644) -
#      nichts wird jemals uebertragen, im Repo committed oder in .env/docker-compose.yml
#      hinterlegt. Bereits vorhandene Secret-Dateien werden NICHT ueberschrieben, damit ein
#      erneuter Aufruf (z.B. bei einem Redeploy) keine Passwoerter rotiert.
#      644 statt 600: Docker-Compose-File-Secrets werden per Bind-Mount mit den
#      Host-Dateirechten in die Container gereicht: App-/Keycloak-Image laufen als
#      Nicht-root-User (z.B. UID 185), der bei 600 (nur Owner-Host-User darf lesen)
#      die Datei nicht lesen kann - "Permission denied" beim Start. 644 ist hier
#      unschaedlich, da die Secrets ohnehin read-only in isolierte Container gemountet
#      werden; die Absicherung gegenueber anderen Host-Usern kommt vom "chmod 700 secrets".
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
        chmod 644 "$file"
    fi
}

echo "🔐 Pruefe/generiere Secrets..."
generate_secret_if_missing db_password
generate_secret_if_missing keycloak_admin_password
generate_secret_if_missing keycloak_admin_cli_secret

# brevo_smtp_password ist KEIN lokal generierbares Passwort, sondern ein externes Zugangsdatum
# aus deinem Brevo-Account (https://app.brevo.com -> SMTP & API -> SMTP-Schluessel) - anders als
# db_password/keycloak_*, die diese Anwendung selbst erzeugt und ausschliesslich lokal braucht.
# Wird nur eine leere Platzhalter-Datei angelegt (falls sie fehlt), NIE ein Zufallswert - eine
# zufaellig generierte "Brevo-Passwort"-Datei wuerde den echten Mailversand mit einem
# Auth-Fehler scheitern lassen, ohne dass das beim Bootstrap auffaellt.
if [[ ! -f secrets/brevo_smtp_password.txt ]]; then
    echo "✨ Lege leere secrets/brevo_smtp_password.txt an (Platzhalter)..."
    touch secrets/brevo_smtp_password.txt
    chmod 644 secrets/brevo_smtp_password.txt
fi
if grep -q "^BREVO_SMTP_USER=" .env 2>/dev/null && [[ ! -s secrets/brevo_smtp_password.txt ]]; then
    echo "⚠️  BREVO_SMTP_USER ist in .env gesetzt, aber secrets/brevo_smtp_password.txt ist leer."
    echo "    Echten SMTP-Schluessel aus deinem Brevo-Account eintragen, sonst schlaegt der Mailversand fehl:"
    echo "    echo '<dein-brevo-smtp-schluessel>' > secrets/brevo_smtp_password.txt && chmod 644 secrets/brevo_smtp_password.txt"
fi

# --- Realm-Template rendern ---
echo "🧩 Rendere keycloak-realm.generated.json..."
# shellcheck disable=SC1091
set -a; source .env; set +a
ADMIN_CLI_SECRET="$(cat secrets/keycloak_admin_cli_secret.txt)"
APP_URL="${APP_PUBLIC_URL:-http://localhost:9000}"

# Dieselben Werte/Defaults wie beim App-eigenen Mailer in docker-compose.yml (QUARKUS_MAILER_*),
# damit Keycloaks nativer "Passwort vergessen"-Flow denselben SMTP-Weg (Mailpit lokal/UTM,
# Brevo in echtem Prod-Betrieb) nutzt wie der Rest der Anwendung.
MAILER_HOST_VAL="${MAILER_HOST:-mailpit}"
MAILER_PORT_VAL="${MAILER_PORT:-1025}"
MAILER_START_TLS_VAL="${MAILER_START_TLS:-DISABLED}"
BREVO_SMTP_USER_VAL="${BREVO_SMTP_USER:-}"
BREVO_SMTP_PASSWORD_VAL="$(cat secrets/brevo_smtp_password.txt)"

# Keycloaks smtpServer-Map erwartet Booleans als Strings "true"/"false". Auth nur, wenn ein
# Benutzername gesetzt ist (Mailpit im lokalen/UTM-Profil braucht keine Authentifizierung).
MAILER_AUTH_VAL="false"
[[ -n "$BREVO_SMTP_USER_VAL" ]] && MAILER_AUTH_VAL="true"
MAILER_STARTTLS_VAL="false"
[[ "$MAILER_START_TLS_VAL" == "REQUIRED" ]] && MAILER_STARTTLS_VAL="true"

# Externe Zugangsdaten (Brevo-Nutzername/-Passwort) landen als Wert INNERHALB eines
# JSON-Strings und brauchen daher zwei Escaping-Schichten: erst JSON-Escaping (\ und " sind in
# JSON-Strings selbst Sonderzeichen), danach sed-Escaping (&, \, der Trennzeichen-Slash wuerden
# die Ersetzung sonst stillschweigend verfaelschen).
json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}
sed_escape_replacement() {
    printf '%s' "$1" | sed -e 's/[&\\|]/\\&/g'
}
escape_for_json_template() {
    sed_escape_replacement "$(json_escape "$1")"
}

sed \
    -e "s|__ADMIN_CLI_SECRET__|$(escape_for_json_template "$ADMIN_CLI_SECRET")|g" \
    -e "s|__APP_PUBLIC_URL__|${APP_URL}|g" \
    -e "s|__MAILER_HOST__|${MAILER_HOST_VAL}|g" \
    -e "s|__MAILER_PORT__|${MAILER_PORT_VAL}|g" \
    -e "s|__MAILER_AUTH__|${MAILER_AUTH_VAL}|g" \
    -e "s|__MAILER_STARTTLS__|${MAILER_STARTTLS_VAL}|g" \
    -e "s|__BREVO_SMTP_USER__|$(escape_for_json_template "$BREVO_SMTP_USER_VAL")|g" \
    -e "s|__BREVO_SMTP_PASSWORD__|$(escape_for_json_template "$BREVO_SMTP_PASSWORD_VAL")|g" \
    keycloak-realm.template.json > keycloak-realm.generated.json
chmod 644 keycloak-realm.generated.json

# --- Pflichtvariablen fuer Profil "public" (Caddy/TLS) ---
# Bewusst hier statt per Compose "${VAR:?...}" geprueft: Compose wertet solche Pflicht-Variablen
# beim Parsen fuer JEDEN Service aus, auch fuer inaktive Profile - das wuerde auch den
# lokalen/UTM-Fall ohne Caddy brechen.
if [[ "${1:-}" == "public" ]]; then
    missing=()
    [[ -z "${APP_HOSTNAME:-}" ]] && missing+=(APP_HOSTNAME)
    [[ -z "${ACME_EMAIL:-}" ]] && missing+=(ACME_EMAIL)
    # Ohne explizit gesetzte KEYCLOAK_PUBLIC_URL wuerde Keycloak in Prod klaglos auf den
    # lokalen Default (http://localhost:8080/auth) zurueckfallen - ein Fehlschlagen hier ist
    # der Fehlkonfiguration im Betrieb vorzuziehen.
    [[ -z "${KEYCLOAK_PUBLIC_URL:-}" ]] && missing+=(KEYCLOAK_PUBLIC_URL)
    if [[ ${#missing[@]} -gt 0 ]]; then
        echo "❌ Profil 'public' braucht folgende Variablen in .env: ${missing[*]}"
        exit 1
    fi
fi

echo "✅ Bereit. Beispiele:"
echo "   Lokal/UTM:    docker compose up -d"
echo "   Mit Mailpit:  docker compose --profile mailpit up -d"
echo "   public: ./bootstrap.sh public && docker compose --profile public --profile monitoring up -d"
