package kreyj.konfplan.adapter.in.web.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class OrganisatorCsvDto {
    @CsvBindByName(column = "Vorname", required = true)
    public String vorname;

    @CsvBindByName(column = "Nachname", required = true)
    public String nachname;

    @CsvBindByName(column = "LoginName", required = true)
    public String loginName;

    @CsvBindByName(column = "Email")
    public String email;
}
