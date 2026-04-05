package kreyj.vortragsmanager.dto;

import com.opencsv.bean.CsvBindByName;

public class VeranstaltungCsvDto {
    @CsvBindByName(column = "Name", required = true)
    public String name;

    @CsvBindByName(column = "Beginn", required = true)
    public String beginntAm; // Format: yyyy-MM-dd HH:mm

    @CsvBindByName(column = "Ende")
    public String endetAm;

    // 'ort' wurde entfernt

    @CsvBindByName(column = "Organisator_Email", required = true)
    public String organisatorEmail;

    @CsvBindByName(column = "Gebaeude_Namen") // Pipe-getrennte Liste von Gebaeude-Namen
    public String gebaeudeNamen;
}
