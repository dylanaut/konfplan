package kreyj.konfplan.persistence;

import lombok.Getter;

@Getter
public enum Berufsfeld {
    LAND_FORST_TIERWIRTSCHAFT_UND_GARTENBAU("Land-, Forst-, Tierwirtschaft und Gartenbau"),
    ROHSTOFFGEWINNUNG_PRODUKTION_UND_FERTIGUNG("Rohstoffgewinnung, Produktion und Fertigung"),
    BAU_ARCHITEKTUR_VERMESSUNG_UND_GEBAEUDETECHNIK("Bau, Architektur, Vermessung und Gebäudetechnik"),
    NATURWISSENSCHAFT_GEOGRAFIE_UND_INFORMATIK("Naturwissenschaft, Geografie und Informatik"),
    VERKEHR_LOGISTIK_SCHUTZ_UND_SICHERHEIT("Verkehr, Logistik, Schutz und Sicherheit"),
    ELEKTROTECHNIK("Elektrotechnik"),
    METALL_MASCHINEN_UND_FAHRZEUGBAU("Metall-, Maschinen- und Fahrzeugbau"),
    IT_UND_COMPUTER("IT und Computer"),
    CHEMIE_KUNSTSTOFF_GLAS_TEXTIL_UND_HOLZ("Chemie, Kunststoff, Glas, Textil und Holz"),
    GASTRONOMIE_LEBENSMITTEL_UND_HAUSWIRTSCHAFT("Gastronomie, Lebensmittel und Hauswirtschaft"),
    GESUNDHEIT("Gesundheit"),
    SOZIALES_PAEDAGOGIK_UND_THEOLOGIE("Soziales, Pädagogik und Theologie"),
    KREATIVBERUFE_MEDIEN_UND_GESTALTUNG("Kreativberufe, Medien und Gestaltung"),
    WIRTSCHAFT_VERWALTUNG_RECHT_UND_GESELLSCHAFT("Wirtschaft, Verwaltung, Recht und Gesellschaft"),
    UNTERNEHMENSFUEHRUNG_ORGANISATION_EINKAUF_VERTRIEB_UND_MARKETING("Unternehmensführung, Organisation, Einkauf, Vertrieb und Marketing"),
    TOURISMUS_SPORT_UND_KULTUR("Tourismus, Sport und Kultur");

    private final String name;

    Berufsfeld(String name) {
        this.name = name;
    }

}
