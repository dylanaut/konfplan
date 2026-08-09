#!/bin/sh
# Wird per systemd-ExecStartPre (konfplan-keycloak.service) vor jedem Start ausgefuehrt.
# Rendert das Realm-Template mit dem tatsaechlichen Admin-CLI-Secret und der oeffentlichen
# App-URL, damit Keycloak es beim Start importieren kann (--import-realm ueberspringt den
# Import automatisch, sobald der Realm bereits existiert - erneutes Rendern ist daher
# unschaedlich, auch bei jedem Neustart).
set -eu

: "${KC_ADMIN_CLI_SECRET:?KC_ADMIN_CLI_SECRET muss in /etc/konfplan/keycloak.env gesetzt sein}"
: "${APP_PUBLIC_URL:?APP_PUBLIC_URL muss in /etc/konfplan/keycloak.env gesetzt sein}"

mkdir -p /var/lib/konfplan-keycloak/import
sed \
    -e "s|__ADMIN_CLI_SECRET__|${KC_ADMIN_CLI_SECRET}|g" \
    -e "s|__APP_PUBLIC_URL__|${APP_PUBLIC_URL}|g" \
    /usr/lib/konfplan-keycloak/konfplan-realm.template.json \
    > /var/lib/konfplan-keycloak/import/konfplan-realm.json
chmod 600 /var/lib/konfplan-keycloak/import/konfplan-realm.json

echo "Realm-Import nach /var/lib/konfplan-keycloak/import/konfplan-realm.json gerendert."
