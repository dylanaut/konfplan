#!/usr/bin/env bash
# Baut konfplan-keycloak_<version>_all.deb (Docker-Wrapper fuer Keycloak).
#
# Uebernimmt das Realm-Template aus deploy/keycloak-realm.template.json (eine Quelle der
# Wahrheit fuer beide Deployment-Verfahren, siehe deploy/bootstrap.sh fuer das Docker-Compose-
# Gegenstueck) statt eine eigene Kopie im Repo zu pflegen.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
REALM_TEMPLATE="$REPO_ROOT/deploy/keycloak-realm.template.json"
PKG_ROOT="$SCRIPT_DIR/pkgroot"

if [[ ! -f "$REALM_TEMPLATE" ]]; then
    echo "Fehler: $REALM_TEMPLATE nicht gefunden." >&2
    exit 1
fi

rm -rf "$PKG_ROOT"
mkdir -p "$PKG_ROOT"
cp -r "$SCRIPT_DIR/DEBIAN" "$PKG_ROOT/DEBIAN"
cp -r "$SCRIPT_DIR/etc" "$PKG_ROOT/etc"
cp -r "$SCRIPT_DIR/lib" "$PKG_ROOT/lib"
cp -r "$SCRIPT_DIR/usr" "$PKG_ROOT/usr"
cp "$REALM_TEMPLATE" "$PKG_ROOT/usr/lib/konfplan-keycloak/konfplan-realm.template.json"

chmod 755 "$PKG_ROOT/DEBIAN/postinst" "$PKG_ROOT/DEBIAN/postrm" "$PKG_ROOT/usr/lib/konfplan-keycloak/ensure-realm.sh"
find "$PKG_ROOT" -type d -exec chmod 755 {} \;

VERSION="$(grep '^Version:' "$PKG_ROOT/DEBIAN/control" | cut -d' ' -f2)"
OUT="$SCRIPT_DIR/konfplan-keycloak_${VERSION}_all.deb"

dpkg-deb --build --root-owner-group "$PKG_ROOT" "$OUT"
rm -rf "$PKG_ROOT"
echo "Erstellt: $OUT"
