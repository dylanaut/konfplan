import argparse
import configparser
import json
import os
import sys

from includes.mp_globals import *

from mp_postprocessor import generate_html_dashboards, generate_event_pdfs, get_base64_image


def import_solution_and_data(data_dir):
    solution_file = os.path.join(data_dir, 'solution.json')

    with open(solution_file, "r", encoding="utf-8") as f:
        solution = json.load(f)

    data_file = os.path.join(data_dir, 'data.json')
    with open(data_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    data['auffuellungen'] = set(tuple(item) for item in data['auffuellungen'])

    return solution['instanz_slot'], solution['instanz_raum'], solution['besucht'], data


def post_process(project_dir):
    if not project_dir.endswith(os.sep) and not project_dir.endswith('/'):
        project_dir += os.sep

    instanz_slot, instanz_raum, besucht, data_csv = import_solution_and_data(project_dir)

    output_dir = os.path.join(project_dir, OUTPUT_DIR)
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    messe_config = {}
    configParser = configparser.ConfigParser()
    config_path = os.path.join(project_dir, MESSE_CONFIG_INI)
    if os.path.exists(config_path):
        configParser.read(config_path, encoding='utf-8')

        messe_config = dict(configParser['MESSE_INFOS']) # ini section
        messe_config['logo_base64'] = get_base64_image(messe_config['messe_logo'])
    else:
        print(f"⚠️ Warnung: Konfigurationsdatei {config_path} nicht gefunden!")

    generate_html_dashboards(instanz_slot, instanz_raum, besucht, data_csv, messe_config, output_dir)
    generate_event_pdfs(instanz_slot, instanz_raum, besucht, data_csv, messe_config, output_dir)

    return True


if __name__ == "__main__":
    # 1. Argparse-Konfiguration
    parser = argparse.ArgumentParser(description='MP Postprocessor - erzeugt Artefakte auf Basis einer exportierten MiniZinc Solution')

    # 2. Das Argument definieren
    # 'nargs="?"' bedeutet: Das Argument ist optional
    # 'default="data/"' wird genommen, wenn nichts übergeben wird
    parser.add_argument('-p', '--pfad', default='NO_PROJECT',
                        help='Pfad zum Projektverzeichnis mit den CSV- und Ergebnisdateien')

    # 3. Argumente auslesen
    args = parser.parse_args()

    # 4. Funktion mit dem übergebenen Pfad aufrufen
    success = post_process(PROJEKTE_DIR + args.pfad)

    # Optional: Exit-Code für Automatisierung (0 = Erfolg, 1 = Fehler)
    if not success:
        sys.exit(1)
