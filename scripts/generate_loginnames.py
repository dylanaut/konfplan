#!/usr/bin/env python3
"""Füllt eine leere LoginName-Spalte in einer Nutzer-CSV.

LoginName = abgekürzter Vorname + "." + Nachname, ASCII-transliteriert (deutsche
Umlaute/ß nach ae/oe/ue/ss, sonstige Diakritika per Unicode-Normalisierung entfernt)
und komplett kleingeschrieben. Bei Kollisionen wird der Vorname stückweise verlängert
(l. -> le. -> leo. ...), erst als letzter Ausweg ein numerisches Suffix angehängt -
das Ergebnis ist innerhalb der Datei garantiert eindeutig. Bereits gefüllte
LoginName-Zellen werden nicht verändert, zählen aber als vergeben.

Beispiel:
    python3 scripts/generate_loginnames.py teilnehmer_roh.csv -o teilnehmer.csv
"""

import argparse
import csv
import re
import unicodedata

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
    return re.sub(r"[^a-z0-9-]+", "", text)


def make_login(vorname, nachname, taken):
    erster_vorname = vorname.strip().split()[0] if vorname.strip() else ""
    vorname_slug = to_ascii_slug(erster_vorname)
    nachname_slug = to_ascii_slug(nachname)

    if not vorname_slug or not nachname_slug:
        return None

    for laenge in range(1, len(vorname_slug) + 1):
        kandidat = f"{vorname_slug[:laenge]}.{nachname_slug}"
        if kandidat not in taken:
            return kandidat

    n = 2
    while True:
        kandidat = f"{vorname_slug}.{nachname_slug}{n}"
        if kandidat not in taken:
            return kandidat
        n += 1


def fill_loginnames(rows, fieldnames, vorname_spalte, nachname_spalte, loginname_spalte):
    taken = {
        row[loginname_spalte].strip().lower()
        for row in rows
        if row[loginname_spalte].strip()
    }

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
    args = parser.parse_args()

    with open(args.csv_datei, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f, delimiter=args.delimiter)
        fieldnames = reader.fieldnames
        rows = list(reader)

    for spalte in (args.vorname_spalte, args.nachname_spalte, args.loginname_spalte):
        if spalte not in fieldnames:
            raise SystemExit(f"Spalte '{spalte}' nicht in der CSV gefunden. Vorhanden: {fieldnames}")

    anzahl_neu = fill_loginnames(rows, fieldnames, args.vorname_spalte, args.nachname_spalte, args.loginname_spalte)

    zieldatei = args.output or args.csv_datei
    with open(zieldatei, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, delimiter=args.delimiter)
        writer.writeheader()
        writer.writerows(rows)

    print(f"{anzahl_neu} LoginName-Werte ergänzt, geschrieben nach '{zieldatei}'.")


if __name__ == "__main__":
    main()
