package kreyj.vortragsmanager.dto;

import com.opencsv.bean.CsvBindByName;

public class RaumCsvDto {
    @CsvBindByName(column = "Name", required = true)
    public String name;

    @CsvBindByName(column = "Kapazitaet", required = true)
    public int kapazitaet;

    @CsvBindByName(column = "Etage")
    public String etage;
}
