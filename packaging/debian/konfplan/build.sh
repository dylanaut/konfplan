#!/usr/bin/env bash
# Baut konfplan_<version>_all.deb aus dem bereits erzeugten Quarkus-fast-jar-Verzeichnis.
#
# Voraussetzung (im Repo-Root ausfuehren):
#   cd backend && ../mvnw clean package -DskipTests
#
# Aufruf:
#   packaging/debian/konfplan/build.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
QUARKUS_APP_DIR="$REPO_ROOT/backend/target/quarkus-app"
PKG_ROOT="$SCRIPT_DIR/pkgroot"

if [[ ! -f "$QUARKUS_APP_DIR/quarkus-run.jar" ]]; then
    echo "Fehler: $QUARKUS_APP_DIR/quarkus-run.jar nicht gefunden." >&2
    echo "Zuerst bauen mit: cd backend && ../mvnw clean package -DskipTests" >&2
    exit 1
fi

rm -rf "$PKG_ROOT"
mkdir -p "$PKG_ROOT/opt/konfplan"
cp -r "$SCRIPT_DIR/DEBIAN" "$PKG_ROOT/DEBIAN"
cp -r "$SCRIPT_DIR/etc" "$PKG_ROOT/etc"
cp -r "$SCRIPT_DIR/lib" "$PKG_ROOT/lib"
cp -r "$QUARKUS_APP_DIR" "$PKG_ROOT/opt/konfplan/quarkus-app"

chmod 755 "$PKG_ROOT/DEBIAN/postinst" "$PKG_ROOT/DEBIAN/postrm"
find "$PKG_ROOT" -type d -exec chmod 755 {} \;

VERSION="$(grep '^Version:' "$PKG_ROOT/DEBIAN/control" | cut -d' ' -f2)"
OUT="$SCRIPT_DIR/konfplan_${VERSION}_all.deb"

dpkg-deb --build --root-owner-group "$PKG_ROOT" "$OUT"
rm -rf "$PKG_ROOT"
echo "Erstellt: $OUT"
