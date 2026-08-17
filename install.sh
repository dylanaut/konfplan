#!/bin/bash
#
# Installations-Skript für die KonfPlan-Anwendung auf einem frischen Debian 13
# (Trixie) Server. Baut die Anwendung aus dem Quellcode und installiert sie
# zusammen mit PostgreSQL, Keycloak und Mailpit über die vier .deb-Pakete unter
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
sudo apt-get install -y openjdk-21-jdk maven nodejs npm minizinc

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker   # oder ab-/anmelden, damit die Gruppenzugehörigkeit greift
docker compose version

# nvm - vgl. https://github.com/nvm-sh/nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.6/install.sh | bash

export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"  # This loads nvm
[ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"  # This loads nvm bash_completion

nvm install 22
nvm use 22
nvm alias default 22


# --- Schritt 2: Anwendung bauen (Backend + Frontend via Quinoa) ---
echo "--> Schritt 2: Anwendung wird gebaut..."
./backend/mvnw clean package -DskipTests

# --- Schritt 3: .deb-Pakete bauen ---
echo "--> Schritt 3: Debian-Pakete werden gebaut..."
packaging/debian/build-all.sh

# --- Schritt 4: PostgreSQL, Keycloak und Mailpit installieren ---
echo "--> Schritt 4: PostgreSQL, Keycloak und Mailpit werden installiert..."
sudo apt-get install -y ./packaging/debian/konfplan-postgresql/konfplan-postgresql_*_all.deb
sudo apt-get install -y ./packaging/debian/konfplan-keycloak/konfplan-keycloak_*_all.deb
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
echo "  sudo nano /etc/konfplan/postgresql.env  # DB_PASSWORD setzen"
echo "  sudo nano /etc/konfplan/keycloak.env     # DB_PASSWORD (= das in postgresql.env), KC_ADMIN_PASSWORD, KC_ADMIN_CLI_SECRET, KC_HOSTNAME, APP_PUBLIC_URL"
echo "  sudo nano /etc/konfplan/mailpit.env      # optional, Standardwerte meist ok"
echo "  sudo nano /etc/konfplan/konfplan.env     # DB_PASSWORD (= das in postgresql.env), KC_ADMIN_CLI_SECRET (= das in keycloak.env), BREVO_SMTP_* oder Mailpit-Variablen"
echo ""
echo "Dann starten (in dieser Reihenfolge):"
echo "  sudo systemctl start konfplan-postgresql.service"
echo "  sudo systemctl start konfplan-keycloak.service"
echo "  sudo systemctl start konfplan-mailpit.service"
echo "  sudo systemctl start konfplan.service"
echo ""
echo "Die Anwendung ist danach unter http://<server-ip>:9000 erreichbar."
echo "Logs: journalctl -u konfplan.service -f"
echo "Details und Troubleshooting: packaging/debian/README.md"
echo "========================================="
