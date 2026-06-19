import base64
import datetime
import os
import re
import shutil
import unicodedata
from pathlib import Path

from fpdf import FPDF, XPos, YPos
from jinja2 import FileSystemLoader, Environment
from pypdf import PdfWriter

from includes.mp_globals import RESOURCES_DIR

WORD_SEPS = {' ', '.', ',', ';'}


def get_base64_image(image_path):
    if not os.path.exists(image_path):
        return ""
    with open(image_path, "rb") as img_file:
        # Bilddaten lesen und kodieren
        encoded_string = base64.b64encode(img_file.read()).decode('utf-8')
        # Format für HTML src festlegen (z.B. image/png oder image/svg+xml)
        extension = os.path.splitext(image_path)[1].replace(".", "")
        return f"data:image/{extension};base64,{encoded_string}"


def copy_file_ifne(dateiname, quell_ordner, ziel_ordner):
    # Pfade als Objekte definieren
    quelle = Path(quell_ordner) / dateiname
    ziel = Path(ziel_ordner) / dateiname

    # 1. Prüfen, ob die Datei im Ziel bereits existiert
    if ziel.exists():
        print(f"Datei '{dateiname}' ist bereits im Zielverzeichnis vorhanden.")
        return True

    # 2. Prüfen, ob die Datei in der Quelle überhaupt existiert
    if not quelle.exists():
        print(f"Fehler: Quelldatei '{quelle}' wurde nicht gefunden!")
        return False

    # 3. Kopieren, falls sie im Ziel fehlt
    try:
        # Erstellt auch den Zielordner, falls er noch nicht existiert
        ziel.parent.mkdir(parents=True, exist_ok=True)

        shutil.copy2(quelle, ziel)  # copy2 erhält auch Metadaten wie Zeitstempel
        print(f"Datei '{dateiname}' wurde erfolgreich nach '{ziel_ordner}' kopiert.")
        return True
    except Exception as e:
        print(f"Ein Fehler ist beim Kopieren aufgetreten: {e}")
        return False


def sort_key_simplified(text):
    # Zerlegt Sonderzeichen in Basisbuchstabe + Akzent
    normalized = unicodedata.normalize('NFKD', text)
    # Filtert alle Zeichen heraus, die "Markierungen" (Akzente) sind
    return "".join([c for c in normalized if not unicodedata.combining(c)])


def add_dejavu_font(self):
    user_fonts_dir = os.path.expanduser("~/Library/Fonts")

    try:
        self.add_font("DejaVu", "", os.path.join(user_fonts_dir, "DejaVuSans.ttf"))
        self.add_font("DejaVu", "B", os.path.join(user_fonts_dir, "DejaVuSans-Bold.ttf"))
        self.add_font("DejaVu", "I", os.path.join(user_fonts_dir, "DejaVuSans-Oblique.ttf"))
    except:
        print("⚠️ Warnung: DejaVu TTF Dateien nicht gefunden. Nutze Standard-Font.")
        self.add_font("DejaVu", "", "")  # Fallback


def merge_pdfs(verzeichnis, pattern, ausgabe_pfad):
    merger = PdfWriter()
    regex = re.compile(pattern)

    alle_dateien = os.listdir(verzeichnis)

    # Filter: Nur PDFs, die dem Regex entsprechen
    passende_dateien = [f for f in alle_dateien if f.endswith('.pdf') and regex.search(f)]
    passende_dateien.sort()

    if not passende_dateien:
        print("Keine passenden Dateien gefunden.")
        return

    for datei in passende_dateien:
        source = os.path.join(verzeichnis, datei)
        merger.append(source)

    with open(ausgabe_pfad, "wb") as output_file:
        merger.write(output_file)

    merger.close()


class LaufzettelPDF(FPDF):
    def __init__(self, messe_config, **kwargs):
        super().__init__(**kwargs)
        self.messe_config = messe_config

        add_dejavu_font(self)

    def draw_student_card(self, y_offset, tn_idx, tn, instanz_slot, instanz_raum, besucht,
                          slots_dict, raeume_dict, wahl_vortraege, pflicht_vortraege):
        """Zeichnet einen A5-Querformat-Bereich auf der A4-Seite - ohne Logo"""
        self.set_y(y_offset)
        self.set_font("DejaVu", "B", 12)
        self.cell(0, 10, f"Laufzettel: {tn['Name']} ({tn['Klasse']})", new_x=XPos.LMARGIN,
                  new_y=YPos.NEXT)

        self.set_font("DejaVu", "B", 9)
        cols = (20, 80, 25, 70)
        headers = ["Zeit", "Vortrag", "Raum", "Notiz"]
        for i, h in enumerate(headers):
            self.cell(cols[i], 7, h, border=1)
        self.ln()

        self.set_font("DejaVu", "", 8)

        s_lauf = [{'date': f"{sl['tag']}, {sl['start']}", 'title': "  -- frei --", 'loc': ""} for sl in
                  slots_dict.values()]

        # Befülle alle Zuweisungen für diesen Schüler
        for v_idx, v_row in enumerate(instanz_slot):
            for i_idx, s_id in enumerate(v_row):
                if besucht[tn_idx][v_idx][i_idx]:
                    r_id = instanz_raum[v_idx][i_idx]
                    raum = raeume_dict[r_id]
                    wv = wahl_vortraege[v_idx]

                    s_lauf[s_id - 1].update({
                        'title': smart_prefix(wv['name'], upto=50),
                        'loc': f"{raum['name']}, {raum['etage']}"})

        for pv in pflicht_vortraege:
            if pv['zielklasse'] == tn['Klasse']:
                raum = raeume_dict[pv['p_raum']]
                slot_id = pv['p_slot']  # zum sortieren

                s_lauf[slot_id - 1].update({'title': smart_prefix(pv['name'], upto=50),
                                            'loc': f"{raum['name']}, {raum['etage']}"})

        if s_lauf is None:
            self.cell(text='Keine Teilnahmen')
        else:
            for sl in s_lauf:
                if sl is None:
                    self.cell(cols[0], 8, '  -- frei --', border=1)
                    self.cell(cols[1], 8, '', border=1)
                    self.cell(cols[2], 8, '', border=1)
                    self.cell(cols[3], 8, '', border=1)  # Checkbox-Platz
                else:
                    self.cell(cols[0], 8, sl['date'], border=1)
                    self.cell(cols[1], 8, sl['title'], border=1)
                    self.cell(cols[2], 8, sl['loc'], border=1)
                    self.cell(cols[3], 8, "", border=1)  # Checkbox-Platz
                self.ln()


class EventPDF(FPDF):
    def __init__(self, messe_config, **kwargs):
        super().__init__(**kwargs)
        self.messe_config = messe_config

        add_dejavu_font(self)

    def header(self):
        # 1. Logo rechts platzieren
        # Parameter: Pfad, x (Seitenbreite - Bildbreite - Rand), y, Breite
        if os.path.exists(self.messe_config['messe_logo']):
            self.image(self.messe_config['messe_logo'], 186, 14, 14)

        self.set_font("DejaVu", "B", 12)
        self.set_xy(10, 10)
        self.cell(100, 5, f"{self.messe_config['messe_titel']}", border=0, ln=True)

        self.set_font("DejaVu", "", 10)
        self.cell(100, 5, f"{self.messe_config['messe_organisator']}, {self.messe_config['messe_termin']}", border=0,
                  ln=True)

        # Platz für den Inhalt lassen
        self.ln(10)


class SpeakerPDF(EventPDF):
    def __init__(self, draft, messe_config, **kwargs):
        super().__init__(messe_config, **kwargs)
        self.draft = draft

    def header(self):
        super().header()

        if self.draft:
            # Schriftart und Farbe (hellgrau) setzen
            self.set_font("helvetica", "B", 44)
            self.set_text_color(224, 224, 224)

            # Den Ursprung für die Drehung festlegen (Mitte der Seite)
            with self.rotation(angle=45, x=70, y=148):
                # Text platzieren (zentriert relativ zur Drehung)
                text = "DRAFT / VORLÄUFIG"
                text_breite = self.get_string_width(text)
                # X berechnen: (Seitenbreite - Textbreite) / 2
                x_mitte = (self.w - text_breite) / 2
                # Y berechnen (ungefähr die Mitte minus halbe Schrifthöhe)
                y_mitte = 148.5 + 11.5
                self.text(x=x_mitte, y=y_mitte, text=text)

            # Wichtig: Farbe zurücksetzen, damit der restliche Text schwarz ist
            self.set_text_color(0, 0, 0)


def aktive_raum_belegungen(instanz_slot, instanz_raum, besucht, pflicht_vortraege, klassengroessen):
    """Filtert alle Instanzen heraus, die keine Teilnehmer haben."""
    active = []

    for pv in pflicht_vortraege:
        active.append(({
            'ist_pflicht': True,
            'v_idx': pv['id'] - 1,
            'i_idx': 0,
            's_id': pv['p_slot'],
            'r_id': pv['p_raum'],
            'belegung': klassengroessen[pv['zielklasse']]
        }))

    # Iteriere über alle Vorträge und deren Instanzen
    for v_idx, v_row in enumerate(instanz_slot):
        for i_idx, s_id in enumerate(v_row):
            if s_id > 0:  # Instanz ist einem Slot zugewiesen
                # Zähle Teilnehmer für genau diese Instanz
                tn_count = sum(1 for p_idx in range(len(besucht))
                               if besucht[p_idx][v_idx][i_idx])

                if tn_count > 0:  # Nur wenn mindestens ein Teilnehmer da ist
                    active.append({
                        'ist_pflicht': False,
                        'v_idx': v_idx,
                        'i_idx': i_idx,
                        's_id': s_id,
                        'r_id': instanz_raum[v_idx][i_idx],
                        'belegung': tn_count
                    })
    return active


def smart_prefix(text, start=15, upto=25):
    for i in range(upto - 1, start, -1):
        if i >= len(text):
            continue

        next_char_idx = i + 1
        if next_char_idx >= len(text) or text[next_char_idx] in WORD_SEPS:
            return text[:i + 1]

    return text[:upto] + '...'


def generate_event_pdfs(instanz_slot, instanz_raum, besucht, data_csv, messe_config, output_dir):
    # --- 0. Daten initialisieren
    wahl_vortraege = data_csv['wahl_vortraege']
    raeume_dict = {r['id']: r for r in data_csv['raeume']}
    ref_dict = {r['id']: r for r in data_csv['referenten']}
    slots_dict = {s['id']: s for s in data_csv['slots']}
    tn_list = data_csv['teilnehmer']
    tn_verfuegbar = data_csv['tn_verfuegbar']
    pflicht_vortraege = data_csv['pflicht_vortraege']

    # --- 1. TEILNEHMER LAUFZETTEL (A5 auf A4) ---
    gen_laufzettel(instanz_slot, instanz_raum, besucht,
                   slots_dict, raeume_dict,
                   wahl_vortraege, pflicht_vortraege,
                   tn_list, messe_config, output_dir)

    # --- 2. ANWESENHEITSLISTEN ---
    gen_anwesenheits_listen(instanz_slot, instanz_raum, besucht, tn_list, raeume_dict, ref_dict,
                            slots_dict, wahl_vortraege, pflicht_vortraege, messe_config, output_dir)

    # --- 3. TUERSCHILDER ---
    gen_tuerschilder(instanz_slot, instanz_raum, besucht, raeume_dict, ref_dict, slots_dict,
                     wahl_vortraege, pflicht_vortraege, tn_list, messe_config, output_dir)

    # --- 4. AUFSICHTSLISTEN ---
    gen_aufsichts_liste(instanz_slot, besucht, tn_list, slots_dict, pflicht_vortraege,
                        tn_verfuegbar, len(wahl_vortraege), messe_config, output_dir)

    # --- 5. ABSTIMMUNGSLISTEN ---
    gen_stimmzettel(wahl_vortraege, messe_config, output_dir)

    # --- 6. ABSTIMMUNGSLISTEN ---
    gen_wahl_vortrags_uebersicht(ref_dict, wahl_vortraege, messe_config, output_dir)

    # --- 7. REFERENTEN EINSATZPLÄNE ---
    gen_einsatzplaene(instanz_slot, instanz_raum, besucht, tn_list, raeume_dict, ref_dict, slots_dict,
                      wahl_vortraege, pflicht_vortraege, messe_config, output_dir)


def gen_laufzettel(instanz_slot, instanz_raum, besucht,
                   slots_dict, raeume_dict, wahl_vortraege,
                   pflicht_vortraege, tn_list, messe_config, output_dir):
    print("Erstelle Laufzettel...")

    lz_pdf = LaufzettelPDF(messe_config=messe_config)
    for i in range(0, len(tn_list), 2):
        lz_pdf.add_page()
        lz_pdf.draw_student_card(10, i, tn_list[i], instanz_slot, instanz_raum, besucht,
                                 slots_dict, raeume_dict, wahl_vortraege, pflicht_vortraege)
        if i + 1 < len(tn_list):
            lz_pdf.line(0, 148.5, 210, 148.5)  # Trennlinie
            lz_pdf.draw_student_card(158, i + 1, tn_list[i + 1], instanz_slot, instanz_raum, besucht,
                                     slots_dict, raeume_dict, wahl_vortraege, pflicht_vortraege)

    output_path = os.path.join(output_dir, "1_Teilnehmer_Laufzettel.pdf")
    lz_pdf.output(output_path)
    print(f"✅ Teilnehmer_Laufzettel gespeichert unter: {output_path}")


def gen_anwesenheits_listen(instanz_slot, instanz_raum, besucht, tn_list, raeume_dict, ref_dict, slots_dict,
                            wahl_vortraege, pflicht_vortraege, messe_config, output_dir):
    pdf_r = EventPDF(messe_config=messe_config)

    print("Erstelle Anwesenheitslisten für Pflichtveranstaltungen...")
    for pv in pflicht_vortraege:
        zielklasse = pv['zielklasse']
        slot_id = pv['p_slot']
        slot = slots_dict[slot_id]
        raum_id = pv['p_raum']
        raum = raeume_dict[raum_id]
        ref = ref_dict[pv['ref_id']]
        besucht_liste = [p_idx for p_idx, tn in enumerate(tn_list) if tn['Klasse'] == zielklasse]

        add_anwesenheit_page(pdf_r, pv, besucht_liste, raum, ref, slot, tn_list)

    print("Erstelle Anwesenheitslisten für Wahlveranstaltungen...")
    for v_idx, v_row in enumerate(instanz_slot):
        for i_idx, s_id in enumerate(v_row):
            if s_id > 0:
                v_meta = wahl_vortraege[v_idx]
                r_id = instanz_raum[v_idx][i_idx]
                raum = raeume_dict[r_id]
                ref = ref_dict[v_meta['ref_id']]
                slot = slots_dict[s_id]

                # Teilnehmer finden
                besucht_liste = [p_idx for p_idx, b in enumerate(besucht) if b[v_idx][i_idx]]

                add_anwesenheit_page(pdf_r, v_meta, besucht_liste, raum, ref, slot, tn_list)

    output_path = os.path.join(output_dir, "2_Anwesenheiten.pdf")
    pdf_r.output(output_path)
    print(f"✅ Anwesenheitslisten gespeichert unter: {output_path}")


def add_anwesenheit_page(pdf_r, v_meta, besucht_liste, raum, ref, slot, tn_list):
    if not besucht_liste:
        return

    pdf_r.add_page()
    pdf_r.set_font("DejaVu", "B", 12)
    pdf_r.cell(0, 5, f"{v_meta['name']}", new_x=XPos.LMARGIN, new_y=YPos.NEXT)

    pdf_r.set_font("DejaVu", "", 12)
    pdf_r.cell(0, 5, f"{ref['name']}, {ref['organisation']}", new_x=XPos.LMARGIN, new_y=YPos.NEXT)
    pdf_r.set_font("DejaVu", "", 11)
    pdf_r.cell(0, 8,
               f"{slot['tag']}, {slot['start']}-{slot['ende']} | Raum: {raum['name']} | Teilnehmer: {len(besucht_liste)}",
               new_x=XPos.LMARGIN,
               new_y=YPos.NEXT)
    pdf_r.ln(5)

    with pdf_r.table(col_widths=(10, 60, 30, 60), line_height=int(pdf_r.font_size * 1.4),
                     padding=0.8) as table:
        h = table.row()
        h.cell("Nr")
        h.cell("Name")
        h.cell("Klasse")
        h.cell("Unterschrift")
        for nr, p_idx in enumerate(besucht_liste, 1):
            p = tn_list[p_idx]
            row = table.row()
            row.cell(str(nr))
            row.cell(p['Name'])
            row.cell(p['Klasse'])
            row.cell("")


def gen_tuerschilder(instanz_slot, instanz_raum, besucht, raeume_dict, ref_dict, slots_dict,
                     wahl_vortraege, pflicht_vortraege, tn_list, messe_config, output_dir):
    print("Erstelle Raum-Türschilder...")

    pdf = EventPDF(messe_config=messe_config)
    pdf.alias_nb_pages()

    eindeutige_klassen = {t["Klasse"] for t in tn_list}
    klassen_groessen = {k: len([t for t in tn_list if t["Klasse"] == k]) for k in eindeutige_klassen}

    # Wir gehen durch alle Räume
    for r_idx, r_info in raeume_dict.items():
        # Finde alle Belegungen für diesen Raum
        belegungen = []
        all_active = aktive_raum_belegungen(instanz_slot, instanz_raum, besucht, pflicht_vortraege, klassen_groessen)

        for a in all_active:
            if a['r_id'] == r_idx:
                slot = slots_dict[a['s_id']]
                v_idx = a['v_idx']
                i_idx = a['i_idx']

                if a['ist_pflicht']:
                    v_meta = pflicht_vortraege[v_idx]
                else:
                    v_meta = wahl_vortraege[v_idx]

                ref = ref_dict.get(v_meta['ref_id'])
                belegungen.append({
                    'slot_id': a['s_id'],
                    'zeit': f"{slot['tag']}, {slot['start']}-{slot['ende']}",
                    'vortrag': v_meta['name'],
                    'referent': f"{ref['name']}\n{ref['organisation']}",
                    'belegung': a['belegung'],
                })

        # Nur Seite erstellen, wenn der Raum auch genutzt wird
        if not belegungen:
            continue

        belegungen.sort(key=lambda x: x['slot_id'])

        pdf.add_page()
        # Großer Raum-Header
        pdf.set_font("DejaVu", "B", 26)
        pdf.cell(0, 20, f"Raum: {r_info['name']}", border=1, new_x=XPos.LMARGIN, new_y=YPos.NEXT, align="C")
        pdf.set_font("DejaVu", "", 14)
        pdf.cell(0, 10, f"Etage: {r_info['etage']} | Kapazität: {r_info['kapazitaet']} Plätze", new_x=XPos.LMARGIN,
                 new_y=YPos.NEXT, align="C")
        pdf.ln(10)

        # Tabelle für den Tagesplan
        pdf.set_font("DejaVu", "B", 12)
        with pdf.table(col_widths=(35, 70, 70, 15), line_height=int(pdf.font_size * 1.2), padding=0.8) as table:
            h = table.row()
            h.cell("Zeit")
            h.cell("Vortrag")
            h.cell("Referent")
            h.cell("#TN")

            pdf.set_font("DejaVu", "", 11)
            for b in belegungen:
                row = table.row()
                row.cell(b['zeit'])
                row.cell(b['vortrag'])
                row.cell(b['referent'])
                row.cell(str(b['belegung']), align="C")

    output_path = os.path.join(output_dir, "3_Raum_Tuerplaene.pdf")
    pdf.output(output_path)
    print(f"✅ Raum-/Türpläne gespeichert unter: {output_path}")


def gen_aufsichts_liste(instanz_slot, besucht, tn_list, slots_dict, pflicht_vortraege, tn_verfuegbar, n_vortraege,
                        messe_config, output_dir):
    print("Erstelle Aufsichtsliste (Nicht verplante Teilnehmer)...")
    pdf = EventPDF(messe_config=messe_config)
    pdf.alias_nb_pages()
    pdf.add_page()

    pdf.set_font("DejaVu", "B", 18)
    pdf.cell(0, 10, "Aufsichtsliste", ln=True, align="C")
    pdf.ln(5)

    verplante_p_pro_slot = {s_id: set() for s_id in slots_dict}

    # 2.a Teilnehmer - Pflicht-Zuweisungen eintragen
    for pv in pflicht_vortraege:
        pfl_slot_id = pv['p_slot']

        pfl_klasse = pv['zielklasse']
        tn_idxs_fuer_klasse = [i for i, tn in enumerate(tn_list) if tn["Klasse"] == pfl_klasse]
        verplante_p_pro_slot[pfl_slot_id].update(tn_idxs_fuer_klasse)

    for s_id, s_info in slots_dict.items():
        # Header
        pdf.set_font("DejaVu", "B", 14)
        pdf.cell(0, 10, f"Aufsichtsliste für {s_info['tag']}, {s_info['start']}-{s_info['ende']}", ln=True)
        pdf.ln(5)

        for v_idx in range(n_vortraege):
            for i_idx, slot_val in enumerate(instanz_slot[v_idx]):
                if slot_val == s_id:
                    for p_idx in range(len(tn_list)):
                        if besucht[p_idx][v_idx][i_idx]:
                            verplante_p_pro_slot[s_id].add(p_idx)

        # 2. Teilnehmer filtern: Nicht verplant UND verfügbar
        aufsichts_kandidaten = []
        for p_idx in range(len(tn_list)):
            if p_idx not in verplante_p_pro_slot[s_id]:
                if tn_verfuegbar[p_idx][s_id - 1]:
                    p = tn_list[p_idx]
                    aufsichts_kandidaten.append({
                        'name': p['Name'],
                        'klasse': p['Klasse']
                    })

        # Nach Klasse und Name sortieren
        aufsichts_kandidaten.sort(key=lambda x: x['name'])

        # 3. Tabelle ausgeben
        pdf.set_font("DejaVu", "B", 10)
        pdf.set_fill_color(240, 240, 240)
        pdf.cell(80, 8, "Name", border=1, fill=True)
        pdf.cell(40, 8, "Klasse", border=1, fill=True)
        pdf.cell(60, 8, "Bemerkung / Ort", border=1, fill=True)
        pdf.ln()

        pdf.set_font("DejaVu", "", 9)
        if not aufsichts_kandidaten:
            pdf.cell(0, 10, "Alle verfügbaren Teilnehmer sind in Vorträgen verplant.", ln=True)
        else:
            for k in aufsichts_kandidaten:
                pdf.cell(80, 5, k['name'], border=1)
                pdf.cell(40, 5, k['klasse'], border=1)
                pdf.cell(60, 5, "", border=1)  # Freifeld für Aufsicht
                pdf.ln()
        pdf.ln(5)

    output_path = os.path.join(output_dir, "4_Aufsichtsliste.pdf")
    pdf.output(output_path)
    print(f"✅ Aufsichtsliste gespeichert unter: {output_path}")


def gen_stimmzettel(wahl_vortraege, messe_config, output_dir):
    print("Erstelle Stimmzettel für Teilnehmer...")
    pdf = LaufzettelPDF(messe_config=messe_config)
    pdf.alias_nb_pages()
    pdf.add_page()

    # Tabellen-Layout berechnen (Breite pro Zelle)
    col_width = pdf.epw / len(wahl_vortraege)

    for cnt in range(4):
        pdf.set_font("DejaVu", "B", 13)
        pdf.cell(0, 10, "Vortragswahl von    ______________________________     Klasse: __________", ln=True)
        pdf.ln(3)

        pdf.set_font("DejaVu", "I", 8)
        pdf.cell(0, 8,
                 "Bitte lege hier Deine Prioritäten von 1 bis 3 (1 für hoch, 3 für niedrig) für Deine Wunschvorträge fest:",
                 ln=True)

        # Zeile 1: Vortragsnummern
        pdf.set_font("DejaVu", "B", 10)
        pdf.set_fill_color(240, 240, 240)
        for i, v in enumerate(wahl_vortraege, start=1):
            pdf.cell(col_width, 8, v['name'], border=1, align="C", fill=True)
        pdf.ln()

        for _ in wahl_vortraege:
            pdf.cell(col_width, 10, "", border=1, align="C")

        if cnt < 3:
            pdf.ln(20)
            pdf.cell(0, 10, "___ ", ln=True)
            pdf.ln(10)

    output_path = os.path.join(output_dir, "5_Stimmzettel.pdf")
    pdf.output(output_path)
    print(f"✅ Stimmzettel gespeichert unter: {output_path}")


def gen_wahl_vortrags_uebersicht(ref_dict, wahl_vortraege, messe_config, output_dir):
    print("Erstelle Wahl-Vortragsübersicht...")
    pdf = EventPDF(messe_config=messe_config)
    pdf.alias_nb_pages()
    pdf.add_page()

    # Vorträge auflisten (Laufende Nummer, Titel, inhalt)
    pdf.set_font("DejaVu", "", 10)

    for i, v in enumerate(wahl_vortraege, start=1):
        ref = ref_dict[v['ref_id']]

        pdf.set_font("DejaVu", "B", 11)
        pdf.multi_cell(0, 5, f"{i}. {v['name']}, {ref['name']}, {ref['organisation']}", ln=True, align='L')

        pdf.set_font("DejaVu", "I", 10)
        org_claim = ref['org_claim']
        if org_claim and org_claim.strip():
            pdf.multi_cell(0, 5, org_claim.strip(), border=0, align='L', ln=True)

        pdf.ln(2)
        pdf.set_font("DejaVu", "", 10)
        pdf.multi_cell(0, 5, v.get('inhalt', 'Keine Beschreibung verfügbar.'),
                       align='L', border=0)
        pdf.ln(3)

    output_path = os.path.join(output_dir, "6_Wahl-Vortragsübersicht.pdf")
    pdf.output(output_path)
    print(f"✅ Wahl-Vortragsübersicht gespeichert unter: {output_path}")


def gen_einsatzplaene(instanz_slot, instanz_raum, besucht, tn_list, raeume_dict, ref_dict, slots_dict, wahl_vortraege,
                      pflicht_vortraege, messe_config, output_dir):
    print("Erstelle Einsatzpläne für Referenten...")
    ref_files = []

    # Wir iterieren über die Referenten, um pro Person eine Seite zu erstellen
    for ref_id, ref_info in ref_dict.items():
        pdf = SpeakerPDF(draft=True, messe_config=messe_config)
        pdf.alias_nb_pages()
        pdf.add_page()

        # Header: Name und Organisation
        pdf.set_font("DejaVu", "B", 14)
        pdf.cell(0, 10, f"Einsatzplan für {ref_info['name']}", ln=True, align="C")
        pdf.set_font("DejaVu", "I", 12)
        pdf.cell(0, 8, f"{ref_info['organisation']}", ln=True, align="C")
        pdf.ln(10)

        # Tabelle vorbereiten
        pdf.set_font("DejaVu", "B", 10)
        pdf.set_fill_color(230, 230, 230)
        pdf.cell(28, 7, "Zeit", border=1, fill=True)
        pdf.cell(100, 7, "Vortrag", border=1, fill=True)
        pdf.cell(30, 7, "Raum", border=1, fill=True)
        pdf.cell(14, 7, "# TN", border=1, fill=True, align="C")
        pdf.ln()

        # Alle Einsätze dieses Referenten sammeln
        einsaetze = []
        # Suche alle Pflicht-Vorträge, die diese ref_id haben

        for pv in pflicht_vortraege:
            if not pv['ref_id'] == ref_id:
                continue

            pfl_slot_id = pv['p_slot']
            slot = slots_dict[pfl_slot_id]
            pfl_raum_id = pv['p_raum']
            pfl_klasse = pv['zielklasse']
            tn_count = sum(1 for tn in tn_list if tn["Klasse"] == pfl_klasse)

            raum = raeume_dict[pfl_raum_id]
            einsaetze.append({
                'slot_id': pfl_slot_id,
                'vortrag': pv['name'],
                'raum': f"{raum['name']}, {raum['etage']}",
                'zeit': f"{slot['tag']}, {slot['start']}-{slot['ende']}",
                'anzahl': tn_count
            })

        # Suche alle Wahl-Vorträge, die diese ref_id haben
        for wv_meta in wahl_vortraege:
            wv_idx = wv_meta['id'] - 1

            if wv_meta.get('ref_id') == ref_id:
                for i_idx, s_id in enumerate(instanz_slot[wv_idx]):
                    if s_id > 0:
                        r_id = instanz_raum[wv_idx][i_idx]
                        tn_count = sum(1 for p in range(len(besucht)) if besucht[p][wv_idx][i_idx])
                        s_meta = slots_dict[s_id]

                        raum = raeume_dict[r_id]
                        einsaetze.append({
                            'slot_id': s_meta['id'],
                            'vortrag': wv_meta['name'],
                            'raum': f"{raum['name']}, {raum['etage']}",
                            'zeit': f"{s_meta['tag']}, {s_meta['start']}",
                            'anzahl': tn_count
                        })

        # Chronologisch nach Slot sortieren
        einsaetze.sort(key=lambda x: x['slot_id'])

        # Tabelle befüllen
        pdf.set_font("DejaVu", "", 9)
        for e in einsaetze:
            # Kombiniert Vortrag und Raum für bessere Platznutzung
            pdf.cell(28, 7, e['zeit'], border=1)
            pdf.cell(100, 7, e['vortrag'], border=1)
            pdf.cell(30, 7, e['raum'], border=1)
            pdf.cell(14, 7, str(e['anzahl']), border=1, align="C")
            pdf.ln()

        if not einsaetze:
            pdf.cell(0, 10, "Keine Einsätze für diesen Referenten geplant.", ln=True)

        clean_ref = re.sub(r'[^\w\s-]', '', ref_info['name'])
        clean_ref = re.sub(r'\s+', '_', clean_ref)

        output_path = os.path.join(output_dir, f"7_Einsatzplan_{clean_ref}.pdf")
        pdf.output(output_path)
        ref_files.append(output_path)

    gesamt_plaene = os.path.join(output_dir, "7_Referenten_Einsatzplaene.pdf")
    merge_pdfs(output_dir, '7_Einsatzplan_*', gesamt_plaene)
    print(f"✅ Referenteneinsatzpläne erstellt und gespeichert unter: {gesamt_plaene}")


def calc_prefs_fulfills_stats(tn_list, besucht):
    prio_prefs = {}
    wv_prefs = {}
    total_prefs = 0
    prio_fulfills = {}
    wv_fulfills = {}
    total_fulfills = 0

    for tn_idx, tn in enumerate(tn_list):
        for v_idx, v_prio in enumerate(tn['Prioritäten']):
            if v_prio > 0:
                total_prefs += 1
                if v_prio not in prio_prefs:
                    prio_prefs[v_prio] = 0
                    prio_fulfills[v_prio] = 0
                prio_prefs[v_prio] += 1

                if v_idx not in wv_prefs:
                    wv_prefs[v_idx] = 0
                    wv_fulfills[v_idx] = 0
                wv_prefs[v_idx] += 1

                if any(b for b in besucht[tn_idx][v_idx]):
                    prio_fulfills[v_prio] += 1
                    wv_fulfills[v_idx] += 1
                    total_fulfills += 1

    prefs_fulfills_stats = {
        'total_prefs': total_prefs,
        'prio_prefs': prio_prefs,
        'wv_prefs': wv_prefs,
        'erfuellungen_gesamt': total_fulfills,
        'prio_erfuellungen': prio_fulfills,
        'auffueller': wv_fulfills
    }
    return prefs_fulfills_stats


def calc_stats(belegung_details, raeume_dict, slots_dict, teilnehmer_erfuellung):
    # Zähler initialisieren
    belegte_plaetze = 0
    kapazitaet_total = 0
    prio1_erfuellt = 0
    prio2_erfuellt = 0
    prio3_erfuellt = 0
    total_wuensche_erfuellt = 0
    unerfuellte = 0

    # 1. Kapazität und belegte Plätze berechnen
    for s_id in slots_dict:
        for r_id, r_info in raeume_dict.items():
            kapazitaet_total += r_info['kapazitaet']
            # Prüfen, ob in diesem Slot/Raum ein Vortrag stattfindet
            if s_id in belegung_details and r_id in belegung_details[s_id]:
                belegte_plaetze += belegung_details[s_id][r_id]['anzahl']

    # 2. Wunsch-Erfüllung (basierend auf der teilnehmer_erfüllung Liste)
    for t in teilnehmer_erfuellung:
        for v_id, v_status in t['wahl_vortraege'].items():
            if v_status['status'] == 'ok-wahl':
                total_wuensche_erfuellt += 1
                if v_status['prio'] == 1:
                    prio1_erfuellt += 1
                elif v_status['prio'] == 2:
                    prio2_erfuellt += 1
                elif v_status['prio'] == 3:
                    prio3_erfuellt += 1
            elif v_status['status'] == 'fail-wahl':
                unerfuellte += 1

    # Das stats-Dictionary für das Template zusammenbauen
    stats = {
        "belegte_plaetze": belegte_plaetze,
        "kapazitaet_total": kapazitaet_total,
        "unerfuellte": unerfuellte,
        "total_wuensche_erfuellt": total_wuensche_erfuellt,
        "prio1": prio1_erfuellt,
        "prio2": prio2_erfuellt,
        "prio3": prio3_erfuellt
    }
    return stats


def www(besucht, p_idx, wv_idx, inst_idx, data_csv, instanz_slot, instanz_raum):
    return wwwi(besucht, p_idx, wv_idx, inst_idx,
                instanz_slot, instanz_raum,
                data_csv['teilnehmer'], data_csv['wahl_vortraege'], data_csv['slots'], data_csv['raeume'])


# alle Indizes bitte 1-basiert verwenden
def wwwi(besucht, p_idx, w_idx, inst_idx, instanz_slot, instanz_raum,
         tn_list, wahl_vortraege, slots_list, raeume_list):
    if besucht[p_idx][w_idx][inst_idx]:
        p = tn_list[p_idx]
        wv_name = wahl_vortraege[w_idx]['name']
        s = slots_list[instanz_slot[w_idx][inst_idx]]
        r = raeume_list[instanz_raum[w_idx][inst_idx]]
        return f"{p['Name']} ({p['Klasse']}): '{wv_name}' am {s['tag']}, {s['start']} in {r['name']}"
    else:
        return "frei"


def generate_html_dashboards(instanz_slot, instanz_raum, besucht, data_csv, messe_config, output_dir):
    print("Erstelle HTML Dashboards...")

    gen_stundenplan_dashboard(besucht, data_csv, messe_config, output_dir, instanz_raum, instanz_slot)
    gen_teilnehmer_dashboard(besucht, data_csv, messe_config, output_dir, instanz_raum, instanz_slot)
    gen_prios_dashboard(besucht, data_csv, messe_config, output_dir, instanz_raum, instanz_slot)

    copy_file_ifne('bootstrap.bundle.min.js', RESOURCES_DIR, output_dir)
    copy_file_ifne('bootstrap.min.css', RESOURCES_DIR, output_dir)


def gen_stundenplan_dashboard(besucht, data_csv, messe_config, output_dir, instanz_raum, instanz_slot):
    tn_list = data_csv['teilnehmer']
    n_personen = len(tn_list)
    n_wahlvortraege = len(instanz_slot)
    slots_dict = {s['id']: s for s in data_csv['slots']}
    auffuellung_set = data_csv['auffuellungen']
    ref_dict = {r['id']: r for r in data_csv['referenten']}
    raeume_dict = {r['id']: r for r in data_csv['raeume']}
    wahl_vortraege_dict = {v['id']: v for v in data_csv['wahl_vortraege']}
    pflicht_vortraege = data_csv['pflicht_vortraege']

    # 1. Datenstrukturen vorbereiten
    tn_verfuegbar = data_csv['tn_verfuegbar']  # je slot
    belegung_details = {}  # ACHTUNG: String-Key! "slot_id"_"raum_id"
    verplante_p_pro_slot = {s_id: set() for s_id in slots_dict}

    # 2.a Teilnehmer - Pflicht-Zuweisungen eintragen
    for pv in pflicht_vortraege:
        pfl_slot_id = pv['p_slot']
        pfl_raum_id = pv['p_raum']
        pfl_key = f"{pfl_slot_id}_{pfl_raum_id}"

        pfl_klasse = pv['zielklasse']
        tn_dict_fuer_klasse = [{'name': tn['Name'], 'p_idx': i} for i, tn in enumerate(tn_list) if
                               tn["Klasse"] == pfl_klasse]
        tn_namen = [x['name'] for x in tn_dict_fuer_klasse]
        verplante_p_pro_slot[pfl_slot_id].update([x['p_idx'] for x in tn_dict_fuer_klasse])

        ref_info = ref_dict[pv['ref_id']]
        belegung_details[pfl_key] = {
            "vortrag_name": pv['name'],
            "referent": ref_info['name'],
            "organisation": ref_info['organisation'],
            "ist_pflicht": True,
            "tn_liste": sorted(tn_namen, key=sort_key_simplified),
            "anzahl": len(tn_namen)
        }

    # 2.b Teilnehmer - Wahl-Zuweisungen eintragen
    for wv_idx in range(n_wahlvortraege):
        for inst_idx, slot_id in enumerate(instanz_slot[wv_idx]):
            if slot_id > 0:
                r_id = instanz_raum[wv_idx][inst_idx]
                tn_namen = []

                for tn_idx in range(n_personen):
                    if besucht[tn_idx][wv_idx][inst_idx]:
                        tn = tn_list[tn_idx]
                        name_str = f"{tn['Name']} ({tn['Klasse']})"
                        tn_namen.append(name_str)
                        verplante_p_pro_slot[slot_id].add(tn_idx)

                # In Dictionary für das Template speichern
                if tn_namen:
                    key = f"{slot_id}_{r_id}"

                    if key in belegung_details.keys():
                        # ACHTUNG: dieser key kann noch nicht vergeben worden sein
                        print(f"\n❌ ❌ Fehler: Belegung für Slot {slot_id} und Raum {r_id} schon vergeben?!")
                        print("❌ Bitte Raumverfügbarkeiten und Wahlvortrags-Slots mit Pflicht-Slots abgleichen!")
                        print("❌ MiniZinc Optimierung MUSS NEU gestartet werden!!!")
                        exit(1)

                    wv = wahl_vortraege_dict[wv_idx + 1]
                    ref_info = ref_dict[wv['ref_id']]
                    belegung_details[key] = {
                        "vortrag_name": f"{wv['name']}",
                        "referent": ref_info['name'],
                        "organisation": ref_info['organisation'],
                        "ist_pflicht": False,
                        "tn_liste": sorted(tn_namen, key=sort_key_simplified),
                        "anzahl": len(tn_namen)
                    }

    # Freie Teilnehmer je Slot ermitteln
    freie_tn_je_slot = {s_id: [] for s_id in slots_dict}
    freie_tn_ids_je_slot = {s_id: [] for s_id in slots_dict}
    for slot_id in slots_dict.keys():
        for tn_idx in range(n_personen):
            # Nur wer NICHT verplant ist UND laut Matrix DA sein muss
            if tn_idx not in verplante_p_pro_slot[slot_id]:
                if tn_verfuegbar[tn_idx][slot_id - 1]:  # Index-Check!
                    tn = tn_list[tn_idx]
                    freie_tn_je_slot[slot_id].append(f"{tn['Name']} ({tn['Klasse']})")
                    freie_tn_ids_je_slot[slot_id].append(tn_idx)

    teilnehmer_erfuellung = []
    wv_ids_sorted = sorted(wahl_vortraege_dict.keys())

    for tn_idx, tn in enumerate(tn_list):
        tn_wvs = {"name": f"{tn['Name']} ({tn['Klasse']})", "wahl_vortraege": {}}

        # Wahlen des Teilnehmers in ein flaches Dict für Speed: {v_id: prio}
        tn_prios = {i: v for i, v in enumerate(tn.get('Prioritäten', []))}

        for wv_id in wv_ids_sorted:
            wv_idx = wv_id - 1  # Annahme: v_id ist 1-basiert, Matrix 0-basiert
            prio = tn_prios.get(wv_idx, 0)

            # Wurde dieser Vortrag besucht? (Suche in allen Instanzen)
            besuchte_instanz = None
            besucht_idx = -1
            for inst_idx, slot_id in enumerate(instanz_slot[wv_idx]):
                if slot_id > 0 and besucht[tn_idx][wv_idx][inst_idx]:
                    besuchte_instanz = inst_idx + 1
                    besucht_idx = inst_idx
                    break

            # Status für Farben und Statistik
            status = "0"
            if prio > 0:
                if besuchte_instanz:
                    status = "+"
                else:
                    status = "-"
            elif (tn_idx, wv_idx, besucht_idx) in auffuellung_set:
                status = "f"

            tn_wvs["wahl_vortraege"][wv_id] = {
                'status': status,
                'prio': prio,
                'instanz': besuchte_instanz
            }
        teilnehmer_erfuellung.append(tn_wvs)

    stats = calc_stats(belegung_details, raeume_dict, slots_dict, teilnehmer_erfuellung)
    stats['anzahl_auffuellung'] = len(auffuellung_set)

    wahl_erfuellung_stats = calc_prefs_fulfills_stats(tn_list, besucht)

    # 3. Rendering
    file_loader = FileSystemLoader('.')
    env = Environment(loader=file_loader)
    template = env.get_template(RESOURCES_DIR + "dashboard_stundenplan_template.html")

    html_out = template.render(
        cfg=messe_config,
        slots=slots_dict,
        raeume=raeume_dict,
        belegung_details=belegung_details,
        n_frei_liste=freie_tn_je_slot,
        stats=stats,
        wahl_erfuellung_stats=wahl_erfuellung_stats,
        wv_dict=wahl_vortraege_dict,
        now=datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))

    output_path = os.path.join(output_dir, 'dashboard_stundenplan.html')
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_out)
    print(f"✅ Stundenplan Dashboard gespeichert unter: {output_path}")


def gen_teilnehmer_dashboard(besucht, data_csv, messe_config, output_dir, instanz_raum, instanz_slot):
    tn_list = data_csv['teilnehmer']
    n_personen = len(tn_list)
    n_wahlvortraege = len(instanz_slot)
    slots_dict = {s['id']: s for s in data_csv['slots']}
    auffuellung_set = data_csv['auffuellungen']
    raeume_dict = {r['id']: r for r in data_csv['raeume']}
    wahl_vortraege_dict = {v['id']: v for v in data_csv['wahl_vortraege']}
    pflicht_vortraege = data_csv['pflicht_vortraege']

    teilnehmer_stundenplan = []
    for tn_idx in range(n_personen):
        tn = tn_list[tn_idx]
        p_name = tn['Name']
        p_klasse = tn['Klasse']

        slots_belegung = {}  # s_id -> {inhalt, typ}

        for slot_id in slots_dict.keys():
            belegung = {"name": "frei", "typ": "frei"}

            # 1. Prüfen, ob Schüler anwesend ist
            if slot_id not in tn['verfuegbare_slots']:
                belegung = {"name": "Abwesend", "typ": "abwesend"}

                # 2. Pflichtvortrag suchen (Falls Slot slot_id der Pflichtslot dieser Klasse ist)
                for pv_meta in pflicht_vortraege:
                    if pv_meta['zielklasse'] == p_klasse and pv_meta['p_slot'] == slot_id:
                        belegung = {'name': pv_meta['name'],
                                    'raum': raeume_dict[pv_meta['p_raum']]['name'],
                                    'typ': 'pflicht'}
            else:
                # 3. Wahlvortrag suchen (über die 'besucht' Matrix vom Solver)
                for wv_idx in range(n_wahlvortraege):
                    inst_slot = instanz_slot[wv_idx]
                    inst_raum = instanz_raum[wv_idx]
                    for inst_idx, s_val in enumerate(inst_slot):
                        if s_val == slot_id and besucht[tn_idx][wv_idx][inst_idx]:
                            wv_meta = wahl_vortraege_dict[wv_idx + 1]
                            belegung['name'] = wv_meta['name']
                            belegung['raum'] = raeume_dict[inst_raum[inst_idx]]['name']
                            if (tn_idx, wv_idx, inst_idx) in auffuellung_set:
                                belegung['typ'] = 'auffuellung'
                            else:
                                belegung['typ'] = 'wahl'

            slots_belegung[slot_id] = belegung

        teilnehmer_stundenplan.append({
            "name": f"{p_name} ({p_klasse})",
            "plan": slots_belegung
        })

    # 3. Rendering
    file_loader = FileSystemLoader('.')
    env = Environment(loader=file_loader)
    template = env.get_template(RESOURCES_DIR + 'dashboard_teilnehmer_template.html')

    html_out = template.render(
        cfg=messe_config,
        slots=slots_dict,
        teilnehmer_stundenplan=teilnehmer_stundenplan,
        now=datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))

    output_path = os.path.join(output_dir, 'dashboard_teilnehmer.html')
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_out)
    print(f"✅ Teilnehmer Dashboard gespeichert unter: {output_path}")


def gen_prios_dashboard(besucht, data_csv, messe_config, output_dir, instanz_raum, instanz_slot):
    tn_list = data_csv['teilnehmer']
    n_personen = len(tn_list)
    n_wahlvortraege = len(instanz_slot)
    slots_dict = {s['id']: s for s in data_csv['slots']}
    auffuellung_set = data_csv['auffuellungen']
    ref_dict = {r['id']: r for r in data_csv['referenten']}
    raeume_dict = {r['id']: r for r in data_csv['raeume']}
    wahl_vortraege_dict = {v['id']: v for v in data_csv['wahl_vortraege']}
    pflicht_vortraege = data_csv['pflicht_vortraege']
    wahl_ids = [v_id for v_id, v in wahl_vortraege_dict.items()]
    num_instanzen_pro_wv = [sum(1 for x in sub if x > 0) for sub in instanz_slot]

    # 1. Datenstrukturen vorbereiten
    tn_verfuegbar = data_csv['tn_verfuegbar']  # je slot
    belegung_details = {}  # ACHTUNG: String-Key! "slot_id"_"raum_id"
    verplante_tn_pro_slot = {s_id: set() for s_id in slots_dict}

    # 2.a Teilnehmer - Pflicht-Zuweisungen für Konsistenzprüfung unten eintragen
    for pv in pflicht_vortraege:
        pfl_slot_id = pv['p_slot']
        pfl_raum_id = pv['p_raum']
        pfl_key = f"{pfl_slot_id}_{pfl_raum_id}"
        belegung_details[pfl_key] = {
            "vortrag_name": 'Pflichtvortrag',
            "ist_pflicht": True,
        }
    #
    #     pfl_klasse = pv['zielklasse']
    #     tn_dict_fuer_klasse = [{'name': tn['Name'], 'p_idx': i} for i, tn in enumerate(tn_list) if
    #                            tn["Klasse"] == pfl_klasse]
    #     tn_namen = [x['name'] for x in tn_dict_fuer_klasse]
    #     verplante_p_pro_slot[pfl_slot_id].update([x['p_idx'] for x in tn_dict_fuer_klasse])
    #
    #     ref_info = ref_dict[pv['ref_id']]
    #     belegung_details[pfl_key] = {
    #         "vortrag_name": pv['name'],
    #         "referent": ref_info['name'],
    #         "organisation": ref_info['organisation'],
    #         "ist_pflicht": True,
    #         "tn_liste": sorted(tn_namen, key=sort_key_simplified),
    #         "anzahl": len(tn_namen)
    #     }

    # 2.b Teilnehmer - Wahl-Zuweisungen eintragen
    for wv_idx in range(n_wahlvortraege):
        for inst_idx, slot_id in enumerate(instanz_slot[wv_idx]):
            if slot_id > 0:
                r_id = instanz_raum[wv_idx][inst_idx]
                tn_namen = []

                for tn_idx in range(n_personen):
                    if besucht[tn_idx][wv_idx][inst_idx]:
                        tn = tn_list[tn_idx]
                        name_str = f"{tn['Name']} ({tn['Klasse']})"
                        tn_namen.append(name_str)
                        verplante_tn_pro_slot[slot_id].add(tn_idx)

                # In Dictionary für das Template speichern
                if tn_namen:
                    key = f"{slot_id}_{r_id}"

                    if key in belegung_details.keys():
                        # ACHTUNG: dieser key kann noch nicht vergeben worden sein
                        print(f"\n❌ ❌ Fehler: Belegung für Slot {slot_id} und Raum {r_id} schon vergeben?!")
                        print("❌ Bitte Raumverfügbarkeiten und Wahlvortrags-Slots mit Pflicht-Slots abgleichen!")
                        print("❌ MiniZinc Optimierung MUSS NEU gestartet werden!!!")
                        exit(1)

                    wv = wahl_vortraege_dict[wv_idx + 1]
                    ref_info = ref_dict[wv['ref_id']]
                    belegung_details[key] = {
                        "vortrag_name": f"{wv['name']}",
                        "referent": ref_info['name'],
                        "organisation": ref_info['organisation'],
                        "ist_pflicht": False,
                        "tn_liste": sorted(tn_namen, key=sort_key_simplified),
                        "anzahl": len(tn_namen)
                    }

    # Freie Teilnehmer je Slot ermitteln
    freie_tn_je_slot = {s_id: [] for s_id in slots_dict}
    freie_tn_ids_je_slot = {s_id: [] for s_id in slots_dict}
    for slot_id in slots_dict.keys():
        for tn_idx in range(n_personen):
            # Nur wer NICHT verplant ist UND laut Matrix DA sein muss
            if tn_idx not in verplante_tn_pro_slot[slot_id]:
                if tn_verfuegbar[tn_idx][slot_id - 1]:  # Index-Check!
                    tn = tn_list[tn_idx]
                    freie_tn_je_slot[slot_id].append(f"{tn['Name']} ({tn['Klasse']})")
                    freie_tn_ids_je_slot[slot_id].append(tn_idx)

    teilnehmer_erfuellung = []
    wv_ids_sorted = sorted(wahl_vortraege_dict.keys())

    for tn_idx, tn in enumerate(tn_list):
        tn_wvs = {"name": f"{tn['Name']} ({tn['Klasse']})", "wahl_vortraege": {}}

        # Wahlen des Teilnehmers in ein flaches Dict für Speed: {v_id: prio}
        tn_prios = {i: v for i, v in enumerate(tn.get('Prioritäten', []))}

        for wv_id in wv_ids_sorted:
            wv_idx = wv_id - 1  # Annahme: v_id ist 1-basiert, Matrix 0-basiert
            prio = tn_prios.get(wv_idx, 0)

            # Wurde dieser Vortrag besucht? (Suche in allen Instanzen)
            besuchte_instanz = None
            besucht_idx = -1
            for inst_idx, slot_id in enumerate(instanz_slot[wv_idx]):
                if slot_id > 0 and besucht[tn_idx][wv_idx][inst_idx]:
                    besuchte_instanz = inst_idx + 1
                    besucht_idx = inst_idx
                    break

            # Status für Farben und Statistik
            status = "0"
            if prio > 0:
                if besuchte_instanz:
                    status = "+"
                else:
                    status = "-"
            elif (tn_idx, wv_idx, besucht_idx) in auffuellung_set:
                status = "f"

            tn_wvs["wahl_vortraege"][wv_id] = {'status': status,
                                               'prio': prio,
                                               'instanz': besuchte_instanz
                                               }
        teilnehmer_erfuellung.append(tn_wvs)

    # 3. Rendering
    file_loader = FileSystemLoader('.')
    env = Environment(loader=file_loader)
    template = env.get_template(RESOURCES_DIR + 'dashboard_prios_template.html')

    html_out = template.render(
        cfg=messe_config,
        slots=slots_dict,
        raeume=raeume_dict,
        instanz_raum=instanz_raum,
        instanz_slot=instanz_slot,
        num_instanzen_pro_wv=num_instanzen_pro_wv,
        teilnehmer_erfuellung=teilnehmer_erfuellung,
        wv_dict=wahl_vortraege_dict,
        ref_dict=ref_dict,
        wahl_ids=wahl_ids,
        now=datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    )

    output_path = os.path.join(output_dir, 'dashboard_prios.html')
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_out)
    print(f"✅ Prios Dashboard gespeichert unter: {output_path}")
