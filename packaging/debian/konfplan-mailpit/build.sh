#!/usr/bin/env bash
# Baut konfplan-mailpit_<version>_all.deb (Docker-Wrapper fuer Mailpit).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PKG_ROOT="$SCRIPT_DIR/pkgroot"

rm -rf "$PKG_ROOT"
mkdir -p "$PKG_ROOT"
cp -r "$SCRIPT_DIR/DEBIAN" "$PKG_ROOT/DEBIAN"
cp -r "$SCRIPT_DIR/etc" "$PKG_ROOT/etc"
cp -r "$SCRIPT_DIR/lib" "$PKG_ROOT/lib"

chmod 755 "$PKG_ROOT/DEBIAN/postinst" "$PKG_ROOT/DEBIAN/postrm"
find "$PKG_ROOT" -type d -exec chmod 755 {} \;

VERSION="$(grep '^Version:' "$PKG_ROOT/DEBIAN/control" | cut -d' ' -f2)"
OUT="$SCRIPT_DIR/konfplan-mailpit_${VERSION}_all.deb"

dpkg-deb --build --root-owner-group "$PKG_ROOT" "$OUT"
rm -rf "$PKG_ROOT"
echo "Erstellt: $OUT"
