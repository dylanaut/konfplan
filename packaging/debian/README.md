# KonfPlan – Debian-13-Pakete

Drei eigenständige `.deb`-Pakete für ein frisches Debian 13 (Trixie) x86_64-System:

| Paket | Inhalt | Abhängigkeiten |
|---|---|---|
| `konfplan` | Die Anwendung selbst (Quarkus fast-jar), systemd-Service `konfplan.service` | `openjdk-21-jre-headless`, `minizinc` (beide aus Debian-Repo) |
| `konfplan-postgresql` | systemd-Oneshot-Service, der Datenbank + Rolle in der lokalen PostgreSQL anlegt | `postgresql` (aus Debian-Repo) |
| `konfplan-mailpit` | systemd-Service, der den offiziellen Mailpit-Container startet | `docker.io` |

## Warum PostgreSQL nativ, aber Mailpit als Docker-Container?

PostgreSQL ist bereits offiziell im Debian-13-Hauptrepository enthalten
(`apt install postgresql`) – das Paket `konfplan-postgresql` installiert
daher keinen eigenen Datenbank-Server, sondern nur einen systemd-Oneshot-
Service, der einmalig die KonfPlan-Datenbank und den zugehörigen
Datenbank-Benutzer in der bereits laufenden PostgreSQL-Instanz anlegt.

Mailpit bietet dagegen kein offizielles `.deb` (nur Install-Skript/statische
Binaries/Docker-Image) und wird deshalb als systemd-verwalteter
Docker-Container abgebildet.

MiniZinc ist ebenfalls bereits offiziell im Debian-13-Hauptrepository
enthalten (`apt install minizinc`) und wird daher nicht selbst gepackt,
sondern nur als `Depends:` referenziert.

## Bauen

Auf einer Maschine mit Java 21, Node/npm (für das Frontend via Quinoa) und
`dpkg-deb` (Teil des `dpkg`-Pakets, auf Debian bereits vorhanden):

```bash
# 1. Backend + Frontend bauen (erzeugt backend/target/quarkus-app/)
cd backend && ../mvnw clean package -DskipTests && cd ..

# 2. Alle drei .deb-Pakete bauen
packaging/debian/build-all.sh
```

Ergebnis: `packaging/debian/konfplan/konfplan_1.0.0-1_all.deb`,
`packaging/debian/konfplan-postgresql/konfplan-postgresql_1.0.0-1_all.deb`,
`packaging/debian/konfplan-mailpit/konfplan-mailpit_1.0.0-1_all.deb`.

Einzelne Pakete lassen sich auch separat bauen, z.B. `packaging/debian/konfplan-postgresql/build.sh`
(hat keine Backend-Build-Abhängigkeit).

## Installieren

Reihenfolge auf dem Ziel-Debian-13-Server:

```bash
# Docker wird nur fuer konfplan-mailpit benoetigt; postgresql zieht apt
# automatisch als Depends von konfplan-postgresql aus dem Debian-Repo nach.
sudo apt-get update
sudo apt-get install -y docker.io

sudo apt-get install -y ./konfplan-postgresql_1.0.0-1_all.deb
sudo apt-get install -y ./konfplan-mailpit_1.0.0-1_all.deb   # optional, nur falls kein externer SMTP-Server genutzt wird
sudo apt-get install -y ./konfplan_1.0.0-1_all.deb           # zieht openjdk-21-jre-headless und minizinc automatisch aus dem Debian-Repo nach
```

`apt-get install -y ./paket.deb` (statt `dpkg -i`) löst dabei automatisch die
`Depends:`-Pakete aus dem Debian-Repository auf.

### Konfiguration vor dem ersten Start

Keiner der drei Services startet automatisch nach der Installation – erst
Konfiguration prüfen/anpassen, dann starten:

```bash
sudo nano /etc/konfplan/postgresql.env  # DB_PASSWORD setzen
sudo systemctl start konfplan-postgresql.service

sudo nano /etc/konfplan/mailpit.env     # optional, Standardwerte meist ok
sudo systemctl start konfplan-mailpit.service

sudo nano /etc/konfplan/konfplan.env    # DB_PASSWORD (= das in postgresql.env), BREVO_SMTP_* oder Mailpit-Variablen
sudo systemctl start konfplan.service
```

`DB_NAME` und `DB_USER` in `konfplan.env` und `postgresql.env` müssen
übereinstimmen (Standard in beiden: `konfplan`) – `konfplan-postgresql.service`
legt Datenbank und Rolle beim Start an, `konfplan.service` verbindet sich
anschließend darauf.

Die App ist danach unter `http://<server>:9000` erreichbar (Port siehe
`quarkus.http.port` in `application.properties`).

### Logs & Status

```bash
journalctl -u konfplan.service -f
journalctl -u konfplan-postgresql.service
systemctl status konfplan.service konfplan-postgresql.service konfplan-mailpit.service
```

## Troubleshooting

- **`konfplan-postgresql.service` schlägt fehl**: Prüfen, ob PostgreSQL selbst
  läuft: `systemctl status postgresql`. Das Oneshot-Skript
  (`/usr/lib/konfplan-postgresql/ensure-db.sh`) nutzt die lokale
  Peer-Authentifizierung des `postgres`-Systembenutzers (Debian-Standard) –
  funktioniert nur, wenn es auf demselben Host wie PostgreSQL läuft.
- **Passwort mit Sonderzeichen**: `DB_PASSWORD` wird unquotiert in ein
  `psql -c`-Kommando eingebettet – ein einfaches Anführungszeichen (`'`) im
  Passwort würde den Befehl brechen. Andere Sonderzeichen sind unproblematisch.
- **Firewall**: Falls `ufw` aktiv ist, müssen die benötigten Ports freigegeben
  werden: `sudo ufw allow 9000/tcp` (App), ggf. `8025/tcp` (Mailpit-Web-UI).
  Port 5432 (PostgreSQL) und 1025 (Mailpit-SMTP) sollten NICHT nach außen
  geöffnet werden, da nur `konfplan.service` lokal darauf zugreifen muss.
- **Deinstallation**: `sudo apt-get remove konfplan konfplan-postgresql konfplan-mailpit`
  stoppt die Services, behält aber Konfigurationsdateien. `apt-get purge`
  entfernt zusätzlich die Konfigurationsdateien und den `konfplan`-System-
  benutzer; die PostgreSQL-Datenbank/-Rolle `konfplan` sowie das eigentliche
  Datenverzeichnis (verwaltet vom `postgresql`-Systempaket) werden dabei
  bewusst NICHT gelöscht (Sicherheitsnetz gegen versehentlichen Datenverlust).

## Verwandte Skripte im Repo

- `install.sh` im Repo-Root ist ein Ein-Kommando-Installationsskript für
  Debian 13, das intern genau diese drei Pakete baut und installiert. Es
  eignet sich für eine schnelle Einzelserver-Installation aus einem
  Repository-Checkout heraus; für mehr Kontrolle (z.B. Pakete vorbauen und
  getrennt verteilen) die Schritte in diesem README einzeln ausführen.
- `db/ensure_prod_db.sh` und `db/ensure_prod_infra.sh` bleiben unverändert als
  Werkzeuge für lokale Entwicklung/Tests (z.B. Native-Image-Runs) bestehen –
  sie starten PostgreSQL/Mailpit als Docker-Container statt über
  systemd/apt und adressieren damit einen anderen Anwendungsfall als die
  hier gebauten Produktionspakete.
