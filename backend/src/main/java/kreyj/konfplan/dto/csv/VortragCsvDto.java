package kreyj.konfplan.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class VortragCsvDto {
    @CsvBindByName(column = "istPflicht")
    public boolean istPflicht;

    @CsvBindByName(column = "Titel", required = true)
    public String titel;

    @CsvBindByName(column = "Referent_Email", required = true)
    public String referentEmail;

    @CsvBindByName(column = "Inhalt")
    public String inhalt;

    @CsvBindByName(column = "wiederholbar")
    public boolean wiederholbar;

    @CsvBindByName(column = "maxWiederholungen")
    public int maxWiederholungen;

    @CsvBindByName(column = "Pflichtgruppe")
    public String pflichtGruppe;
    @CsvBindByName(column = "Pflichtraum")
    public String pflichtRaum;

    @CsvBindByName(column = "Pflichtslot")
    public String pflichtSlot;
}
