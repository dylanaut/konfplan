package kreyj.konfplan.adapter.in.web.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class TeilnehmerCsvDto {

    @CsvBindByName(column = "Vorname", required = true)
    public String vorname;

    @CsvBindByName(column = "Nachname", required = true)
    public String nachname;

    @CsvBindByName(column = "LoginName", required = true)
    public String loginName;

    @CsvBindByName(column = "Email")
    public String email;

    @CsvBindByName(column = "Gruppen") // getrennt durch '|'
    public String gruppen;
}
