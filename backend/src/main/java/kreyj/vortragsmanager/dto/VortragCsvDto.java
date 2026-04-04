package kreyj.vortragsmanager.dto;

import com.opencsv.bean.CsvBindByName;

public class VortragCsvDto {
    @CsvBindByName(column = "Titel", required = true)
    public String titel;

    @CsvBindByName(column = "Referent_Email", required = true)
    public String referentEmail;

    @CsvBindByName(column = "Inhalt")
    public String inhalt;

    @CsvBindByName(column = "Zielgruppe")
    public String zielgruppe;

    @CsvBindByName(column = "istPflicht")
    public boolean istPflicht;

    @CsvBindByName(column = "wiederholbar")
    public boolean wiederholbar;

    @CsvBindByName(column = "maxWiederholungen")
    public int maxWiederholungen;

    @CsvBindByName(column = "pflichtraumName")
    public String pflichtraumName;

    @CsvBindByName(column = "pflichtslotBeschreibung")
    public String pflichtslotBeschreibung;
}
