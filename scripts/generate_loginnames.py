#!/usr/bin/env python3
"""Füllt eine leere LoginName-Spalte in einer Nutzer-CSV.

LoginName = vollständiger erster Vorname + "." + Nachname, ASCII-transliteriert
(deutsche Umlaute/ß nach ae/oe/ue/ss, sonstige Diakritika per Unicode-Normalisierung
entfernt) und komplett kleingeschrieben. Bindestriche in Vor- oder Nachname werden
durch Punkt ersetzt (z.B. "Anna-Lena" -> "anna.lena"). Bei Kollisionen (identischer
LoginName) wird eine fortlaufende Nummer an den Nachnamen angehängt (max.mustermann,
max.mustermann2, ...) - das Ergebnis ist innerhalb der Datei garantiert eindeutig.
Bereits gefüllte LoginName-Zellen werden nicht verändert, zählen aber als vergeben.

Zusätzlich werden die LoginNamen bereits existierender Nutzer aus einer laufenden
Datenbank geladen und wie in der CSV bereits vergebene Namen behandelt. Dadurch
werden auch Dubletten bzgl. Vorname/Nachname zwischen CSV und Datenbank korrekt
aufgelöst (z.B. wird aus "j.schmidt" "jo.schmidt", wenn "j.schmidt" schon in der
Datenbank existiert). Benötigt dafür das Paket "psycopg2" (`pip install psycopg2-binary`).

Beispiel:
    python3 scripts/generate_loginnames.py teilnehmer_roh.csv -o teilnehmer.csv
    python3 scripts/generate_loginnames.py teilnehmer_roh.csv --db-url "jdbc:postgresql://localhost:65432/quarkus"
    python3 scripts/generate_loginnames.py teilnehmer_roh.csv --skip-db-check
"""

import argparse
import csv
import re
import sys
import unicodedata
from urllib.parse import parse_qs, urlparse

# Default entspricht der Postgres-DevService in application.properties (%dev-Profil,
# fester Port 65432); Zugangsdaten sind die Quarkus-DevServices-Vorgaben.
DEFAULT_DB_URL = "jdbc:postgresql://localhost:65432/quarkus"
DEFAULT_DB_USER = "quarkus"
DEFAULT_DB_PASSWORD = "quarkus"

GERMAN_MAP = str.maketrans({
    "ä": "ae", "ö": "oe", "ü": "ue", "ß": "ss",
    "Ä": "Ae", "Ö": "Oe", "Ü": "Ue",
})


def to_ascii_slug(text):
    text = text.translate(GERMAN_MAP)
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = text.encode("ascii", "ignore").decode("ascii")
    text = text.lower()
    text = re.sub(r"[^a-z0-9-]+", "", text)
    return text.replace("-", ".")


def make_login(vorname, nachname, taken):
    erster_vorname = vorname.strip().split()[0] if vorname.strip() else ""
    vorname_slug = to_ascii_slug(erster_vorname)
    nachname_slug = to_ascii_slug(nachname)

    if not vorname_slug or not nachname_slug:
        return None

    kandidat = f"{vorname_slug}.{nachname_slug}"
    if kandidat not in taken:
        return kandidat

    n = 2
    while True:
        kandidat = f"{vorname_slug}.{nachname_slug}{n}"
        if kandidat not in taken:
            return kandidat
        n += 1


def jdbc_url_to_dsn(jdbc_url, user, password):
    url = jdbc_url[len("jdbc:"):] if jdbc_url.startswith("jdbc:") else jdbc_url
    parsed = urlparse(url)
    query = parse_qs(parsed.query)
    dsn_user = query.get("user", [user])[0]
    dsn_password = query.get("password", [password])[0]
    dbname = parsed.path.lstrip("/")
    return (
        f"host={parsed.hostname} port={parsed.port or 5432} "
        f"dbname={dbname} user={dsn_user} password={dsn_password}"
    )


def fetch_existing_loginnames(db_url, db_user, db_password):
    try:
        import psycopg2
    except ImportError:
        raise SystemExit(
            "Für --db-url wird das Paket 'psycopg2' benötigt (pip install psycopg2-binary), "
            "oder die Prüfung kann mit --skip-db-check übersprungen werden."
        )

    dsn = jdbc_url_to_dsn(db_url, db_user, db_password)
    try:
        with psycopg2.connect(dsn) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT login_name FROM nutzer")
                return {row[0].strip().lower() for row in cur.fetchall() if row[0]}
    except psycopg2.OperationalError as e:
        raise SystemExit(
            f"Verbindung zur Datenbank '{db_url}' fehlgeschlagen: {e}\n"
            "Läuft die Datenbank (z.B. via 'mvnw quarkus:dev')? "
            "Andernfalls kann die Prüfung mit --skip-db-check übersprungen werden."
        )


def fill_loginnames(rows, fieldnames, vorname_spalte, nachname_spalte, loginname_spalte, db_loginnames=()):
    taken = {
        row[loginname_spalte].strip().lower()
        for row in rows
        if row[loginname_spalte].strip()
    }
    taken.update(db_loginnames)

    anzahl_neu = 0
    for row in rows:
        if row[loginname_spalte].strip():
            continue
        login = make_login(row[vorname_spalte], row[nachname_spalte], taken)
        if login is None:
            continue
        row[loginname_spalte] = login
        taken.add(login)
        anzahl_neu += 1

    return anzahl_neu


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv_datei", help="Eingabe-CSV")
    parser.add_argument("-o", "--output", help="Zieldatei (Default: Eingabedatei wird überschrieben)")
    parser.add_argument("--delimiter", default=";")
    parser.add_argument("--vorname-spalte", default="Vorname")
    parser.add_argument("--nachname-spalte", default="Nachname")
    parser.add_argument("--loginname-spalte", default="LoginName")
    parser.add_argument(
        "--db-url",
        default=DEFAULT_DB_URL,
        help=f"JDBC-URL der Datenbank, gegen die auf vorhandene LoginNamen geprüft wird (Default: {DEFAULT_DB_URL}, "
             "entspricht dem Quarkus DEV-Mode)",
    )
    parser.add_argument("--db-user", default=DEFAULT_DB_USER)
    parser.add_argument("--db-password", default=DEFAULT_DB_PASSWORD)
    parser.add_argument(
        "--skip-db-check",
        action="store_true",
        help="Keine Datenbankverbindung aufbauen, nur innerhalb der CSV auf Eindeutigkeit prüfen",
    )
    args = parser.parse_args()

    db_loginnames = set()
    if not args.skip_db_check:
        db_loginnames = fetch_existing_loginnames(args.db_url, args.db_user, args.db_password)
        print(f"{len(db_loginnames)} vorhandene LoginNamen aus der Datenbank geladen.", file=sys.stderr)

    with open(args.csv_datei, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f, delimiter=args.delimiter)
        fieldnames = reader.fieldnames
        rows = list(reader)

    for spalte in (args.vorname_spalte, args.nachname_spalte, args.loginname_spalte):
        if spalte not in fieldnames:
            raise SystemExit(f"Spalte '{spalte}' nicht in der CSV gefunden. Vorhanden: {fieldnames}")

    anzahl_neu = fill_loginnames(
        rows, fieldnames, args.vorname_spalte, args.nachname_spalte, args.loginname_spalte, db_loginnames
    )

    zieldatei = args.output or args.csv_datei
    with open(zieldatei, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, delimiter=args.delimiter)
        writer.writeheader()
        writer.writerows(rows)

    print(f"{anzahl_neu} LoginName-Werte ergänzt, geschrieben nach '{zieldatei}'.")


if __name__ == "__main__":
    main()
