package kreyj.konfplan.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class ReferentCsvDto {
    @CsvBindByName(column = "Vorname", required = true)
    public String firstName;

    @CsvBindByName(column = "Nachname", required = true)
    public String lastName;

    @CsvBindByName(column = "Email", required = true)
    public String email;

    @CsvBindByName(column = "Position")
    public String jobRole;

    @CsvBindByName(column = "Organisation")
    public String organisation;

    @CsvBindByName(column = "Slogan")
    public String slogan;

    @CsvBindByName(column = "Biografie")
    public String biography;
}
