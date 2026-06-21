package kreyj.konfplan.presentation.dto.csv;

import com.opencsv.bean.CsvBindByName;

import java.util.List;

public class TeilnehmerCsvDto {

    @CsvBindByName(column = "Vorname", required = true)
    public String vorname;

    @CsvBindByName(column = "Nachname", required = true)
    public String nachname;

    @CsvBindByName(column = "Email", required = true)
    public String email;

    @CsvBindByName(column = "Gruppen") // getrennt durch '|'
    public String gruppen;
}
