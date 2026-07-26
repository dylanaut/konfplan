#!/bin/bash
#
# Installations-Skript für die KonfPlan-Anwendung auf einem frischen Ubuntu 22.04 Server.
# Führen Sie dieses Skript als Benutzer mit sudo-Rechten aus.
#
set -e # Bricht das Skript bei einem Fehler sofort ab.

# --- Konfigurierbare Variablen ---
APP_DIR="/opt/konfplan"
APP_USER="konfplan"
GIT_REPO_URL="https://github.com/DEIN_BENUTZER/DEIN_REPO.git" # <-- BITTE ANPASSEN
DB_NAME="konfplan_prod"
DB_USER="konfplan_user"
DB_PASSWORD="DEIN_SEHR_SICHERES_PASSWORT" # <-- BITTE ÄNDERN

echo "=== KonfPlan Installations-Skript ==="

# --- Schritt 1: System-Vorbereitung und Abhängigkeiten ---
echo "--> Schritt 1: System wird aktualisiert und Abhängigkeiten werden installiert..."
sudo apt-get update
sudo apt-get upgrade -y
sudo apt-get install -y openjdk-21-jdk maven git wget unzip

# --- Schritt 2: PostgreSQL-Datenbank installieren und einrichten ---
echo "--> Schritt 2: PostgreSQL wird installiert und konfiguriert..."
sudo apt-get install -y postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Datenbank und Benutzer erstellen
sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME};"
sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH ENCRYPTED PASSWORD '${DB_PASSWORD}';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};"

echo "Datenbank '${DB_NAME}' und Benutzer '${DB_USER}' erstellt."

# --- Schritt 3: MiniZinc installieren ---
echo "--> Schritt 3: MiniZinc wird installiert..."
# Lade die neueste stabile Version von MiniZinc für Linux von GitHub
# Passe die Version bei Bedarf an.
MINIZINC_VERSION="2.8.3"
wget "https://github.com/MiniZinc/MiniZincIDE/releases/download/${MINIZINC_VERSION}/MiniZincIDE-${MINIZINC_VERSION}-x86_64-linux.AppImage" -O /tmp/minizinc.AppImage
chmod +x /tmp/minizinc.AppImage
# Entpacken und an einen systemweiten Ort verschieben
# Das AppImage ist ein Archiv, das man mit --appimage-extract entpacken kann.
cd /tmp
./minizinc.AppImage --appimage-extract
sudo mv squashfs-root /opt/MiniZinc
# Symlinks erstellen, damit 'minizinc' systemweit verfügbar ist
sudo ln -sf /opt/MiniZinc/usr/bin/minizinc /usr/local/bin/minizinc
sudo ln -sf /opt/MiniZinc/usr/bin/fzn-gecode /usr/local/bin/fzn-gecode
sudo ln -sf /opt/MiniZinc/usr/bin/fzn-or-tools /usr/local/bin/fzn-or-tools
cd -
echo "MiniZinc erfolgreich installiert."

# --- Schritt 4: Anwendungs-Benutzer und Verzeichnis erstellen ---
echo "--> Schritt 4: Anwendungs-Benutzer und Verzeichnis werden erstellt..."
sudo useradd -r -m -d ${APP_DIR} -s /bin/bash ${APP_USER} || echo "Benutzer ${APP_USER} existiert bereits."
sudo chown -R ${APP_USER}:${APP_USER} ${APP_DIR}

# --- Schritt 5: Anwendung aus Git klonen und bauen ---
echo "--> Schritt 5: Anwendung wird aus Git geklont und gebaut..."
sudo -u ${APP_USER} git clone ${GIT_REPO_URL} ${APP_DIR}/source
cd ${APP_DIR}/source

# Baue die Anwendung als produktionsfertige JAR-Datei
# Das '-Pprod'-Profil wird oft für Produktionseinstellungen verwendet (optional)
sudo -u ${APP_USER} ./mvnw clean install -Dquarkus.package.type=jar -DskipTests

# Finde die erstellte Runner-JAR
RUNNER_JAR=$(find backend/target -name "*-runner.jar")
sudo -u ${APP_USER} cp ${RUNNER_JAR} ${APP_DIR}/app.jar

# --- Schritt 6: application.properties für die Produktion erstellen ---
echo "--> Schritt 6: application.properties für die Produktion wird erstellt..."
sudo -u ${APP_USER} bash -c "cat <<EOF > ${APP_DIR}/application.properties
# Quarkus HTTP-Konfiguration
quarkus.http.host=0.0.0.0
quarkus.http.port=8080

# Datenbank-Konfiguration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USER}
quarkus.datasource.password=${DB_PASSWORD}
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/${DB_NAME}

# Flyway Migration
quarkus.flyway.migrate-at-start=true

# JWT-Konfiguration (Beispiel, passe den Issuer an)
mp.jwt.verify.publickey.location=META-INF/resources/publicKey.pem
mp.jwt.verify.issuer=https://konfplan.example.com

# Logging
quarkus.log.level=INFO
quarkus.log.file.enable=true
quarkus.log.file.path=${APP_DIR}/logs/konfplan.log
quarkus.log.file.rotation.max-file-size=10M
quarkus.log.file.rotation.max-backup-index=5

# MiniZinc Threads
# Diese Eigenschaft musst du in deiner Anwendung definieren und verwenden
konfplan.minizinc.threads=4
EOF"

# --- Schritt 7: Systemd Service für die Anwendung einrichten ---
echo "--> Schritt 7: Systemd Service wird eingerichtet..."
sudo bash -c "cat <<EOF > /etc/systemd/system/konfplan.service
[Unit]
Description=KonfPlan Application
After=network.target postgresql.service

[Service]
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/java -jar ${APP_DIR}/app.jar -Dquarkus.config.locations=${APP_DIR}/application.properties
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF"

# Service aktivieren und starten
sudo systemctl daemon-reload
sudo systemctl enable konfplan.service
sudo systemctl start konfplan.service

echo "KonfPlan Service gestartet."

# --- Schritt 8: Firewall konfigurieren ---
echo "--> Schritt 8: Firewall wird konfiguriert..."
sudo ufw allow ssh
sudo ufw allow 8080/tcp
sudo ufw --force enable

echo "Firewall aktiviert und Port 8080 geöffnet."

echo "========================================="
echo "Installation abgeschlossen!"
echo "Die Anwendung läuft auf http://DEINE_SERVER_IP:8080"
echo "Logs findest du unter ${APP_DIR}/logs/konfplan.log"
echo "Status des Services: sudo systemctl status konfplan.service"
echo "========================================="
