#!/bin/sh
# Wird per systemd-Oneshot-Service (konfplan-postgresql.service) ausgefuehrt.
# Legt Rolle und Datenbank idempotent an, sobald PostgreSQL laeuft. Nutzt die
# lokale Peer-Authentifizierung des "postgres"-Systembenutzers (Debian-Standard).
#
# HINWEIS: DB_PASSWORD wird unquotiert in ein psql -c Kommando eingebettet -
# ein einfaches Anfuehrungszeichen (') im Passwort wuerde den Befehl brechen.
set -eu

: "${DB_NAME:=konfplan}"
: "${DB_USER:=konfplan}"
: "${DB_PASSWORD:?DB_PASSWORD muss in /etc/konfplan/postgresql.env gesetzt sein}"

if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" | grep -q 1; then
    sudo -u postgres psql -c "CREATE ROLE \"$DB_USER\" LOGIN PASSWORD '$DB_PASSWORD';"
    echo "Rolle '$DB_USER' angelegt."
else
    sudo -u postgres psql -c "ALTER ROLE \"$DB_USER\" WITH PASSWORD '$DB_PASSWORD';"
    echo "Rolle '$DB_USER' existierte bereits, Passwort aktualisiert."
fi

if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then
    sudo -u postgres createdb -O "$DB_USER" "$DB_NAME"
    echo "Datenbank '$DB_NAME' angelegt (Owner: $DB_USER)."
else
    echo "Datenbank '$DB_NAME' existiert bereits."
fi

# Zusaetzliche, separate Datenbank fuer Keycloak (siehe konfplan-keycloak-Paket) - dieselbe
# Rolle wie die App, aber eigene Datenbank. Wird immer angelegt (auch ohne installiertes
# konfplan-keycloak-Paket) - eine ungenutzte leere Datenbank ist harmlos, das haelt dieses
# Skript einfacher als eine Bedingtheit auf ein anderes Paket.
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='keycloak'" | grep -q 1; then
    sudo -u postgres createdb -O "$DB_USER" "keycloak"
    echo "Datenbank 'keycloak' angelegt (Owner: $DB_USER)."
else
    echo "Datenbank 'keycloak' existiert bereits."
fi

echo "PostgreSQL: Datenbanken '$DB_NAME'+'keycloak' und Rolle '$DB_USER' sind bereit."
