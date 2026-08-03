#!/bin/bash
#
# Installations-Skript für die KonfPlan-Anwendung auf einem frischen Debian 13
# (Trixie) Server. Baut die Anwendung aus dem Quellcode und installiert sie
# zusammen mit MS SQL Server und Mailpit über die drei .deb-Pakete unter
# packaging/debian/ (siehe packaging/debian/README.md für Details und
# Troubleshooting).
#
# Voraussetzung: Dieses Skript wird aus einem geklonten Checkout des
# Repositories heraus im Repo-Root ausgeführt, als Benutzer mit sudo-Rechten.
#
set -e # Bricht das Skript bei einem Fehler sofort ab.

echo "=== KonfPlan Installations-Skript (Debian 13) ==="

# --- Schritt 1: System-Vorbereitung ---
echo "--> Schritt 1: System wird aktualisiert und Basis-Abhängigkeiten werden installiert..."
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk maven nodejs npm docker.io minizinc

# --- Schritt 2: Anwendung bauen (Backend + Frontend via Quinoa) ---
echo "--> Schritt 2: Anwendung wird gebaut..."
(cd backend && ../mvnw clean package -DskipTests)

# --- Schritt 3: .deb-Pakete bauen ---
echo "--> Schritt 3: Debian-Pakete werden gebaut..."
packaging/debian/build-all.sh

# --- Schritt 4: MS SQL Server und Mailpit installieren (Docker-Wrapper-Pakete) ---
echo "--> Schritt 4: MS SQL Server und Mailpit werden installiert..."
sudo apt-get install -y ./packaging/debian/konfplan-mssql/konfplan-mssql_*_all.deb
sudo apt-get install -y ./packaging/debian/konfplan-mailpit/konfplan-mailpit_*_all.deb

# --- Schritt 5: Anwendung installieren ---
echo "--> Schritt 5: KonfPlan wird installiert..."
sudo apt-get install -y ./packaging/debian/konfplan/konfplan_*_all.deb

# --- Schritt 6: Firewall konfigurieren (falls ufw vorhanden) ---
if command -v ufw > /dev/null 2>&1; then
    echo "--> Schritt 6: Firewall wird konfiguriert..."
    sudo ufw allow ssh
    sudo ufw allow 9000/tcp
    sudo ufw --force enable
    echo "Firewall aktiviert und Port 9000 geöffnet."
fi

echo ""
echo "========================================="
echo "Installation abgeschlossen!"
echo ""
echo "Vor dem ersten Start noch konfigurieren:"
echo "  sudo nano /etc/konfplan/mssql.env      # MSSQL_SA_PASSWORD setzen"
echo "  sudo nano /etc/konfplan/mailpit.env    # optional, Standardwerte meist ok"
echo "  sudo nano /etc/konfplan/konfplan.env   # DB_PASSWORD (= MSSQL_SA_PASSWORD), BREVO_SMTP_* oder Mailpit-Variablen"
echo ""
echo "Dann starten (in dieser Reihenfolge):"
echo "  sudo systemctl start konfplan-mssql.service"
echo "  sudo systemctl start konfplan-mailpit.service"
echo "  sudo systemctl start konfplan.service"
echo ""
echo "Die Anwendung ist danach unter http://<server-ip>:9000 erreichbar."
echo "Logs: journalctl -u konfplan.service -f"
echo "Details und Troubleshooting: packaging/debian/README.md"
echo "========================================="
