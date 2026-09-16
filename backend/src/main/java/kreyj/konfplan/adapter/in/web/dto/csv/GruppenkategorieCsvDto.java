package kreyj.konfplan.adapter.in.web.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class GruppenkategorieCsvDto {

    @CsvBindByName(column = "Kategorie", required = true)
    public String kategorie;

    @CsvBindByName(column = "Mehrwertig")
    public boolean mehrwertig;

    @CsvBindByName(column = "Pflicht")
    public boolean pflicht;

    @CsvBindByName(column = "Werte", required = true) // getrennt durch '|'
    public String werte;
}
