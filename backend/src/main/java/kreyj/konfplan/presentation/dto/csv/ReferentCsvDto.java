package kreyj.konfplan.presentation.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class ReferentCsvDto {
    @CsvBindByName(column = "Vorname", required = true)
    public String vorname;

    @CsvBindByName(column = "Nachname", required = true)
    public String nachname;

    @CsvBindByName(column = "Email", required = true)
    public String email;

    @CsvBindByName(column = "Position")
    public String position;

    @CsvBindByName(column = "Organisation")
    public String organisation;

    @CsvBindByName(column = "Slogan")
    public String slogan;

    @CsvBindByName(column = "Biografie")
    public String biografie;
}
