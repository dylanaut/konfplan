#!/usr/bin/env bash
set -euo pipefail

# Stellt ein per Administrator-Export (siehe DatabaseBackupResource, Datei
# "konfplan-backup_*.zip") erzeugtes Backup wieder her - restauriert BEIDE Datenbanken
# (konfplan + keycloak) auf dieser Docker-Compose-Instanz. Bewusst KEIN Web-/API-Trigger: ein
# Restore ueberschreibt bestehende Daten vollstaendig, das gehoert nicht hinter einen Button
# (siehe Deployment-DockerCompose.adoc, Abschnitt "Backup-Hinweise").
#
# Aufruf: ./restore_db.sh /pfad/zu/konfplan-backup_2026-09-05_120000.zip
# Muss aus dem deploy/-Verzeichnis laufen (liest secrets/ und ruft "docker compose" relativ auf).

if [ "$#" -ne 1 ]; then
  echo "Aufruf: $0 <pfad-zum-backup.zip>" >&2
  exit 1
fi

BACKUP_ZIP="$1"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f "$BACKUP_ZIP" ]; then
  echo "Backup-Datei nicht gefunden: $BACKUP_ZIP" >&2
  exit 1
fi

if [ ! -f "secrets/db_password.txt" ]; then
  echo "secrets/db_password.txt nicht gefunden - falsches Verzeichnis? Muss aus deploy/ laufen." >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

unzip -q "$BACKUP_ZIP" -d "$WORKDIR"
if [ ! -f "$WORKDIR/konfplan.dump" ] || [ ! -f "$WORKDIR/keycloak.dump" ]; then
  echo "ZIP enthält nicht die erwarteten Dateien konfplan.dump + keycloak.dump." >&2
  exit 1
fi

echo "⚠️  Dies überschreibt die Datenbanken 'konfplan' UND 'keycloak' auf dieser Instanz VOLLSTÄNDIG"
echo "   mit dem Inhalt von: $BACKUP_ZIP"
echo "   Die Container 'app' und 'keycloak' werden dafür kurz gestoppt."
read -r -p "Zum Fortfahren 'JA' eingeben: " CONFIRM
if [ "$CONFIRM" != "JA" ]; then
  echo "Abgebrochen."
  exit 1
fi

# Offene Verbindungen von app/keycloak wuerden "pg_restore --clean" sonst blockieren
# (Objekte lassen sich nicht droppen, solange andere Sessions sie referenzieren).
echo "⏸  Stoppe app + keycloak..."
docker compose stop app keycloak

restore_db() {
  local db_name="$1"
  local dump_file="$2"
  echo "♻️  Stelle Datenbank '$db_name' wieder her..."
  docker compose exec -T postgres pg_restore -U postgres -d "$db_name" --clean --if-exists < "$dump_file"
}

restore_db konfplan "$WORKDIR/konfplan.dump"
restore_db keycloak "$WORKDIR/keycloak.dump"

echo "▶️  Starte app + keycloak wieder..."
docker compose start app keycloak

echo "✅ Restore abgeschlossen."
