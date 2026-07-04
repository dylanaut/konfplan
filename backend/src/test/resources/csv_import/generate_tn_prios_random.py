#!/usr/bin/env python3
"""
Erzeugt eine zufällige Prioritäten-CSV (tn_prios_random.csv) für einen
KonfPlan-Veranstaltungs-Datensatz, analog zum Format von tn_prios.csv.

Liest teilnehmer.csv (Spalte "Email") und wahl_vortraege.csv (Spalten
"istPflicht", "Titel") aus dem übergebenen Verzeichnis. Für jeden
Teilnehmer werden zufällig N Wahlvorträge (N zwischen --min und --max)
ohne Wiederholung ausgewählt und mit eindeutigen, zufälligen Prio-Werten
zwischen 1 und 10 versehen (10 = höchste Priorität, 1 = niedrigste;
siehe CLAUDE.md / tn_prios.csv-Format).

Nutzung:
    generate_tn_prios_random.py <verzeichnis> [--min N] [--max N] [--seed N]

Beispiel:
    ./generate_tn_prios_random.py ../csv_import/karrierekompass-linz-2026 --min 3 --max 5
"""

import argparse
import csv
import random
import sys
from pathlib import Path

LEGENDE_PREFIX = "# Legende:"
CSV_PRIO_HEADER = "Teilnehmer E-Mail;Prioritäten"
PRIO_MIN = 1
PRIO_MAX = 10


def read_teilnehmer_emails(pfad):
    with pfad.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f, delimiter=";")
        if "Email" not in (reader.fieldnames or []):
            sys.exit(f"Fehler: Spalte 'Email' fehlt in {pfad} (gefunden: {reader.fieldnames})")
        emails = [row["Email"].strip() for row in reader if row.get("Email", "").strip()]
    if not emails:
        sys.exit(f"Fehler: Keine Teilnehmer mit E-Mail in {pfad} gefunden.")
    return emails


def read_wahlvortrag_titel(pfad):
    with pfad.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f, delimiter=";")
        for spalte in ("istPflicht", "Titel"):
            if spalte not in (reader.fieldnames or []):
                sys.exit(f"Fehler: Spalte '{spalte}' fehlt in {pfad} (gefunden: {reader.fieldnames})")
        titel = [
            row["Titel"].strip()
            for row in reader
            if row.get("istPflicht", "").strip().lower() != "true" and row.get("Titel", "").strip()
        ]
    if not titel:
        sys.exit(f"Fehler: Keine Wahlvorträge (istPflicht=false) mit Titel in {pfad} gefunden.")
    return titel


def eindeutige_legenden_praefixe(titel_liste):
    """Kürzt jeden Titel auf den Teil vor dem ersten Komma (Komma trennt Legenden-Einträge),
    da die Legende zeilenweise per split(',') geparst wird. Bricht ab, wenn zwei
    Wahlvorträge dadurch denselben (oder einen sich überschneidenden) Präfix erhalten."""
    praefixe = []
    for titel in titel_liste:
        praefix = titel.split(",", 1)[0].strip()
        praefixe.append(praefix if praefix else titel.strip())

    for i, a in enumerate(praefixe):
        for b in praefixe[i + 1:]:
            if a == b or a.startswith(b) or b.startswith(a):
                sys.exit(
                    "Fehler: Mehrdeutige Legenden-Präfixe für die Wahlvorträge:\n"
                    f"  '{a}' und '{b}'\n"
                    "Bitte die betroffenen Vortragstitel in wahl_vortraege.csv eindeutiger gestalten."
                )
    return praefixe


def main():
    parser = argparse.ArgumentParser(
        description="Erzeugt eine zufällige tn_prios_random.csv für einen KonfPlan-Veranstaltungs-Datensatz."
    )
    parser.add_argument("verzeichnis", type=Path, help="Verzeichnis des Veranstaltungs-Datensatzes (enthält teilnehmer.csv, wahl_vortraege.csv)")
    parser.add_argument("--min", type=int, default=3, dest="min_anzahl", help="Minimale Anzahl Wahlvorträge je Teilnehmer (Default: 3)")
    parser.add_argument("--max", type=int, default=5, dest="max_anzahl", help="Maximale Anzahl Wahlvorträge je Teilnehmer (Default: 5)")
    parser.add_argument("--seed", type=int, default=None, help="Optionaler Zufalls-Seed für reproduzierbare Ergebnisse")
    args = parser.parse_args()

    if args.min_anzahl < 1 or args.max_anzahl < args.min_anzahl:
        sys.exit(f"Fehler: Ungültige Range --min {args.min_anzahl} --max {args.max_anzahl}.")
    if args.max_anzahl > (PRIO_MAX - PRIO_MIN + 1):
        sys.exit(f"Fehler: --max darf {PRIO_MAX - PRIO_MIN + 1} nicht überschreiten (nur {PRIO_MAX - PRIO_MIN + 1} eindeutige Prio-Werte verfügbar).")

    verzeichnis = args.verzeichnis
    teilnehmer_csv = verzeichnis / "teilnehmer.csv"
    wahlvortraege_csv = verzeichnis / "wahl_vortraege.csv"
    for pfad in (teilnehmer_csv, wahlvortraege_csv):
        if not pfad.is_file():
            sys.exit(f"Fehler: Datei nicht gefunden: {pfad}")

    if args.seed is not None:
        random.seed(args.seed)

    emails = read_teilnehmer_emails(teilnehmer_csv)
    titel_liste = read_wahlvortrag_titel(wahlvortraege_csv)
    praefixe = eindeutige_legenden_praefixe(titel_liste)

    max_anzahl = min(args.max_anzahl, len(titel_liste))
    if max_anzahl < args.max_anzahl:
        print(f"Hinweis: Nur {len(titel_liste)} Wahlvorträge vorhanden, --max auf {max_anzahl} reduziert.", file=sys.stderr)
    min_anzahl = min(args.min_anzahl, max_anzahl)

    ausgabe_pfad = verzeichnis / "tn_prios_random.csv"
    with ausgabe_pfad.open("w", encoding="utf-8", newline="\n") as f:
        legende = ", ".join(f"{i + 1}={praefix}" for i, praefix in enumerate(praefixe))
        f.write(f"{LEGENDE_PREFIX} {legende}\n")
        f.write(f"{CSV_PRIO_HEADER}\n")

        for email in emails:
            anzahl = random.randint(min_anzahl, max_anzahl)
            gewaehlte_indizes = random.sample(range(1, len(titel_liste) + 1), k=anzahl)
            prio_werte = random.sample(range(PRIO_MIN, PRIO_MAX + 1), k=anzahl)
            prioritaeten = ",".join(f"{idx}:{prio}" for idx, prio in zip(gewaehlte_indizes, prio_werte))
            f.write(f"{email};{prioritaeten}\n")

    print(f"OK: {ausgabe_pfad} mit {len(emails)} Teilnehmern und {len(titel_liste)} Wahlvorträgen erzeugt "
          f"(je {min_anzahl}-{max_anzahl} Prioritäten pro Teilnehmer).")


if __name__ == "__main__":
    main()
