#!/usr/bin/env bash
# Baut alle vier KonfPlan-Debian-Pakete.
#
# Voraussetzung fuer konfplan_*.deb (Backend muss vorher gebaut sein):
#   cd backend && ../mvnw clean package -DskipTests
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/konfplan/build.sh"
"$SCRIPT_DIR/konfplan-postgresql/build.sh"
"$SCRIPT_DIR/konfplan-keycloak/build.sh"
"$SCRIPT_DIR/konfplan-mailpit/build.sh"

echo ""
echo "Alle Pakete erstellt:"
find "$SCRIPT_DIR" -maxdepth 2 -name "*.deb"
