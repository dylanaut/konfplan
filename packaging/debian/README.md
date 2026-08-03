# KonfPlan – Debian-13-Pakete

Drei eigenständige `.deb`-Pakete für ein frisches Debian 13 (Trixie) x86_64-System:

| Paket | Inhalt | Abhängigkeiten |
|---|---|---|
| `konfplan` | Die Anwendung selbst (Quarkus fast-jar), systemd-Service `konfplan.service` | `openjdk-21-jre-headless`, `minizinc` (beide aus Debian-Repo) |
| `konfplan-mssql` | systemd-Service, der den offiziellen MS-SQL-Server-Container startet | `docker.io` |
| `konfplan-mailpit` | systemd-Service, der den offiziellen Mailpit-Container startet | `docker.io` |

## Warum MS SQL Server als Docker-Container?

Microsoft unterstützt `mssql-server` offiziell nur nativ für RHEL, SLES und
Ubuntu – **nicht** für Debian (siehe
[Microsoft-Dokumentation](https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-setup)).
Der von Microsoft selbst empfohlene Weg für alle anderen Linux-Distributionen
ist der offizielle Docker-Container `mcr.microsoft.com/mssql/server`. Das
Paket `konfplan-mssql` bildet das als systemd-verwalteten Docker-Container ab,
statt ein inoffizielles/instabiles natives Paket zu erzwingen.

Mailpit bietet aus demselben Grund (kein offizielles `.deb`) ebenfalls nur
eine Docker-basierte Variante an.

MiniZinc ist dagegen bereits offiziell im Debian-13-Hauptrepository enthalten
(`apt install minizinc`) und wird daher nicht selbst gepackt, sondern nur als
`Depends:` referenziert.

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
`packaging/debian/konfplan-mssql/konfplan-mssql_1.0.0-1_all.deb`,
`packaging/debian/konfplan-mailpit/konfplan-mailpit_1.0.0-1_all.deb`.

Einzelne Pakete lassen sich auch separat bauen, z.B. `packaging/debian/konfplan-mssql/build.sh`
(hat keine Backend-Build-Abhängigkeit).

## Installieren

Reihenfolge auf dem Ziel-Debian-13-Server:

```bash
# Docker wird für konfplan-mssql/-mailpit benötigt
sudo apt-get update
sudo apt-get install -y docker.io

sudo apt-get install -y ./konfplan-mssql_1.0.0-1_all.deb
sudo apt-get install -y ./konfplan-mailpit_1.0.0-1_all.deb   # optional, nur falls kein externer SMTP-Server genutzt wird
sudo apt-get install -y ./konfplan_1.0.0-1_all.deb           # zieht openjdk-21-jre-headless und minizinc automatisch aus dem Debian-Repo nach
```

`apt-get install -y ./paket.deb` (statt `dpkg -i`) löst dabei automatisch die
`Depends:`-Pakete aus dem Debian-Repository auf.

### Konfiguration vor dem ersten Start

Keiner der drei Services startet automatisch nach der Installation – erst
Konfiguration prüfen/anpassen, dann starten:

```bash
sudo nano /etc/konfplan/mssql.env      # MSSQL_SA_PASSWORD setzen
sudo systemctl start konfplan-mssql.service

sudo nano /etc/konfplan/mailpit.env    # optional, Standardwerte meist ok
sudo systemctl start konfplan-mailpit.service

sudo nano /etc/konfplan/konfplan.env   # DB_PASSWORD (= MSSQL_SA_PASSWORD), BREVO_SMTP_* oder Mailpit-Variablen
sudo systemctl start konfplan.service
```

Die App ist danach unter `http://<server>:9000` erreichbar (Port siehe
`quarkus.http.port` in `application.properties`).

### Logs & Status

```bash
journalctl -u konfplan.service -f
journalctl -u konfplan-mssql.service -f
systemctl status konfplan.service konfplan-mssql.service konfplan-mailpit.service
```

## Troubleshooting

- **`docker: permission denied` / Service startet nicht**: Die systemd-Units
  laufen als root (Standard bei `System`-Services ohne `User=`), daher kein
  Problem mit der `docker`-Gruppe. Prüfen, ob der Docker-Daemon überhaupt
  läuft: `systemctl status docker`.
- **MSSQL_SA_PASSWORD wird abgelehnt**: SQL Server verlangt mind. 8 Zeichen
  aus 3 von 4 Zeichenklassen (Groß-/Kleinbuchstaben, Ziffern, Sonderzeichen).
- **Erster Start von `konfplan-mssql` dauert lange / Timeout**: Beim allerersten
  Start wird das ca. 1,5 GB große Image aus dem Internet geladen
  (`TimeoutStartSec=300` ist entsprechend großzügig gesetzt). Bei Bedarf vorher
  manuell `docker pull mcr.microsoft.com/mssql/server:2022-latest` ausführen.
- **Firewall**: Falls `ufw` aktiv ist, müssen die benötigten Ports freigegeben
  werden: `sudo ufw allow 9000/tcp` (App), ggf. `8025/tcp` (Mailpit-Web-UI).
  Port 1433 (SQL Server) und 1025 (Mailpit-SMTP) sollten NICHT nach außen
  geöffnet werden, da nur `konfplan.service` lokal darauf zugreifen muss.
- **Lizenzierung MS SQL Server**: `MSSQL_PID=Developer` (Standard in
  `mssql.env`) ist kostenlos, aber nicht für Produktivbetrieb lizenziert. Für
  echten Produktionsbetrieb entweder `MSSQL_PID=Express` (kostenlos, limitiert
  auf 10 GB Datenbankgröße) oder eine lizenzierte Edition verwenden.
- **Deinstallation**: `sudo apt-get remove konfplan konfplan-mssql konfplan-mailpit`
  stoppt die Services, behält aber Konfigurationsdateien und
  Datenbank-Volumes (`/var/lib/konfplan/mssql-data`). `apt-get purge` entfernt
  zusätzlich die Konfigurationsdateien und den `konfplan`-Systembenutzer,
  löscht aber weiterhin NICHT die SQL-Server-Datenbankdateien (Sicherheitsnetz
  gegen versehentlichen Datenverlust – siehe Ausgabe von `postrm`).

## Verwandte Skripte im Repo

- `install.sh` im Repo-Root ist ein Ein-Kommando-Installationsskript für
  Debian 13, das intern genau diese drei Pakete baut und installiert (Schritte
  1-6). Es eignet sich für eine schnelle Einzelserver-Installation aus einem
  Repository-Checkout heraus; für mehr Kontrolle (z.B. Pakete vorbauen und
  getrennt verteilen) die Schritte in diesem README einzeln ausführen.
- `db/ensure_prod_db.sh` und `db/ensure_prod_infra.sh` bleiben unverändert als
  Werkzeuge für lokale Entwicklung/Tests (z.B. Native-Image-Runs auf
  Apple-Silicon-Macs mit Azure SQL Edge) bestehen – sie adressieren einen
  anderen Anwendungsfall als die hier gebauten Produktionspakete.
