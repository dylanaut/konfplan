package kreyj.konfplan.persistence;

public enum Neigung {
    EMPATHISCH(Kategorie.SOZIAL_TEAMORIENTIERT, "Empathisch",
            "Gut im Einfühlen in andere Menschen (wichtig für soziale Berufe)."),
    KOMMUNIKATIV(Kategorie.SOZIAL_TEAMORIENTIERT, "Kommunikativ",
            "Freude am Austausch und Gespräch mit Kunden oder Kollegen."),
    HILFSBEREIT(Kategorie.SOZIAL_TEAMORIENTIERT, "Hilfsbereit",
            "Motiviert, anderen Unterstützung im Alltag zu bieten."),
    TEAMFAEHIG(Kategorie.SOZIAL_TEAMORIENTIERT, "Teamfähig",
            "Bereit und fähig, Hand in Hand mit anderen an einem Ziel zu arbeiten."),
    DURCHSETZUNGSSTARK(Kategorie.SOZIAL_TEAMORIENTIERT, "Durchsetzungsstark",
            "Fähig, die eigenen Interessen oder die eines Teams standhaft zu vertreten."),

    ANALYTISCH(Kategorie.ANALYTISCH_STRUKTURIERT, "Analytisch",
            "Logisch denkend, gut im Erkennen von Mustern und Datenströmen."),
    SORGFAELTIG(Kategorie.ANALYTISCH_STRUKTURIERT, "Sorgfältig",
            "Detailorientiert, präzise und fehlerfrei arbeitend."),
    STRUKTURIERT(Kategorie.ANALYTISCH_STRUKTURIERT, "Strukturiert",
            "Organisiert, behält stets den Überblick über Aufgaben und Fristen."),
    LOESUNGSORIENTIERT(Kategorie.ANALYTISCH_STRUKTURIERT, "Lösungsorientiert",
            "Fokussiert auf das Finden von Auswegen bei Problemen."),
    AUSDAUERND(Kategorie.ANALYTISCH_STRUKTURIERT, "Ausdauernd",
            "Bleibt auch bei langwierigen oder schwierigen Aufgaben am Ball."),

    KREATIV(Kategorie.KREATIV_INNOVATIV, "Kreativ",
            "Ideenreich, findet unkonventionelle Wege und gestaltet gerne."),
    NEUGIERIG(Kategorie.KREATIV_INNOVATIV, "Neugierig",
            "Offen für Neues, möchte ständig dazulernen und Dinge hinterfragen."),
    IMPROVISATIONSBEREIT(Kategorie.KREATIV_INNOVATIV, "Improvisationsbereit",
            "Kann schnell und flexibel auf unerwartete Situationen reagieren."),
    VISIONAER(Kategorie.KREATIV_INNOVATIV, "Visionär",
            "Denkt langfristig und bringt innovative Konzepte voran."),

    PRAGMATISCH(Kategorie.PRAKTISCH_MACHEND, "Pragmatisch",
            "Praktisch veranlagt, packt Dinge direkt und unkompliziert an."),
    BELASTBAR(Kategorie.PRAKTISCH_MACHEND, "Belastbar",
            "Bleibt auch unter Zeitdruck oder körperlicher Anstrengung ruhig."),
    SELBSTSTAENDIG(Kategorie.PRAKTISCH_MACHEND, "Selbstständig",
            "Kann ohne ständige Anleitung eigenverantwortlich arbeiten.");

    public enum Kategorie {
        SOZIAL_TEAMORIENTIERT("Soziale & Teamorientierte Eigenschaften"),
        ANALYTISCH_STRUKTURIERT("Analytische & Strukturierte Eigenschaften"),
        KREATIV_INNOVATIV("Kreative & Innovative Eigenschaften"),
        PRAKTISCH_MACHEND("Praktische & Machende Eigenschaften");

        private final String bezeichnung;

        Kategorie(String bezeichnung) {
            this.bezeichnung = bezeichnung;
        }

        public String getBezeichnung() {
            return bezeichnung;
        }
    }

    private final Kategorie kategorie;
    private final String bezeichnung;
    private final String beschreibung;

    Neigung(Kategorie kategorie, String bezeichnung, String beschreibung) {
        this.kategorie = kategorie;
        this.bezeichnung = bezeichnung;
        this.beschreibung = beschreibung;
    }

    public Kategorie getKategorie() {
        return kategorie;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public String getBeschreibung() {
        return beschreibung;
    }
}
