package kreyj.konfplan.persistence;

public enum Veranlagung {
    SOZIAL("sozial",
            "Interesse an Tätigkeiten mit und für Menschen, z.B. in Beratung, Pflege oder Erziehung."),
    ORGANISATORISCH("organisatorisch",
            "Freude am Planen, Koordinieren und Strukturieren von Abläufen und Prozessen."),
    MEDIZINISCH("medizinisch",
            "Interesse an Gesundheit, Pflege und der Versorgung kranker oder hilfsbedürftiger Menschen."),
    TECHNISCH("technisch",
            "Interesse am Verstehen, Konstruieren und Bedienen von Maschinen, Geräten und technischen Systemen."),
    KAUFMAENNISCH("kaufmännisch",
            "Interesse an Handel, Verwaltung, Buchhaltung und wirtschaftlichen Zusammenhängen."),
    KREATIV("kreativ",
            "Freude am Gestalten, Entwerfen und Entwickeln neuer Ideen und Konzepte."),
    SPORTLICH("sportlich",
            "Interesse an Bewegung, körperlicher Aktivität und sportlicher Betätigung."),
    HANDWERKLICH("handwerklich",
            "Freude am praktischen, manuellen Arbeiten und Herstellen von Dingen mit den Händen."),
    WISSENSCHAFTLICH("wissenschaftlich",
            "Interesse am systematischen Forschen, Analysieren und Erklären von Zusammenhängen."),
    OEFFENTLICH("öffentlich",
            "Interesse an Tätigkeiten im öffentlichen Dienst, in Verwaltung und Gemeinwesen."),
    RECHTLICH("rechtlich",
            "Interesse an Gesetzen, Regeln und der rechtlichen Bewertung von Sachverhalten."),
    OEKOLOGISCH("ökologisch",
            "Interesse an Umwelt-, Klima- und Nachhaltigkeitsthemen.");

    private final String bezeichnung;
    private final String beschreibung;

    Veranlagung(String bezeichnung, String beschreibung) {
        this.bezeichnung = bezeichnung;
        this.beschreibung = beschreibung;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public String getBeschreibung() {
        return beschreibung;
    }
}
