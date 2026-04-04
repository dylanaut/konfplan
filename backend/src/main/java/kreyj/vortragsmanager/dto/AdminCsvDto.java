package kreyj.vortragsmanager.dto;

import com.opencsv.bean.CsvBindByName;

public class AdminCsvDto {
    @CsvBindByName(column = "Vorname", required = true)
    public String firstName;

    @CsvBindByName(column = "Nachname", required = true)
    public String lastName;

    @CsvBindByName(column = "Email", required = true)
    public String email;
}
