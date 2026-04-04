package kreyj.vortragsmanager.dto;

import com.opencsv.bean.CsvBindByName;

public class VeranstaltungCsvDto {
    @CsvBindByName(column = "Name", required = true)
    public String name;

    @CsvBindByName(column = "Beginn", required = true)
    public String beginntAm; // Format: yyyy-MM-dd HH:mm

    @CsvBindByName(column = "Ende")
    public String endetAm;

    @CsvBindByName(column = "Ort", required = true)
    public String ort;

    @CsvBindByName(column = "Organisator_Email", required = true)
    public String organisatorEmail;
}
