package kreyj.konfplan.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class RaumCsvDto {
    @CsvBindByName(column = "Name", required = true)
    public String name;

    @CsvBindByName(column = "Kapazitaet", required = true)
    public int kapazitaet;

    @CsvBindByName(column = "Etage")
    public String etage;

    @CsvBindByName(column = "Gebaeude_Name", required = true)
    public String gebaeudeName;
}
