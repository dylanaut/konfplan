package kreyj.konfplan.adapter.in.web.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class VeranstaltungCsvDto {
    @CsvBindByName(column = "Name", required = true)
    public String name;

    @CsvBindByName(column = "Beginn", required = true)
    public String beginntAm; // Format: yyyy-MM-dd HH:mm

    @CsvBindByName(column = "Ende")
    public String endetAm;

    @CsvBindByName(column = "Logo")
    public String logo;

    @CsvBindByName(column = "Logo_Link")
    public String logo_link;

    @CsvBindByName(column = "Organisatoren_Emails", required = true)
    public String organisatorenEmails;

    @CsvBindByName(column = "Gebaeude_Namen") // Pipe-getrennte Liste von Gebaeude-Namen
    public String gebaeudeNamen;

    @CsvBindByName(column = "Gruppen") // Pipe-getrennte Liste von Gruppen
    public String gruppen;
}
