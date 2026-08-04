#!/usr/bin/env bash
# Baut konfplan-postgresql_<version>_all.deb (natives PostgreSQL-Setup, kein
# Docker-Wrapper - PostgreSQL ist offiziell in Debian 13 paketiert).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_ROOT="$SCRIPT_DIR/pkgroot"

rm -rf "$PKG_ROOT"
mkdir -p "$PKG_ROOT"
cp -r "$SCRIPT_DIR/DEBIAN" "$PKG_ROOT/DEBIAN"
cp -r "$SCRIPT_DIR/etc" "$PKG_ROOT/etc"
cp -r "$SCRIPT_DIR/lib" "$PKG_ROOT/lib"
cp -r "$SCRIPT_DIR/usr" "$PKG_ROOT/usr"

chmod 755 "$PKG_ROOT/DEBIAN/postinst" "$PKG_ROOT/DEBIAN/postrm" "$PKG_ROOT/usr/lib/konfplan-postgresql/ensure-db.sh"
find "$PKG_ROOT" -type d -exec chmod 755 {} \;

VERSION="$(grep '^Version:' "$PKG_ROOT/DEBIAN/control" | cut -d' ' -f2)"
OUT="$SCRIPT_DIR/konfplan-postgresql_${VERSION}_all.deb"

dpkg-deb --build --root-owner-group "$PKG_ROOT" "$OUT"
rm -rf "$PKG_ROOT"
echo "Erstellt: $OUT"
