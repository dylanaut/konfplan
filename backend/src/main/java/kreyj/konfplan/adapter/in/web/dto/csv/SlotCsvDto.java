package kreyj.konfplan.adapter.in.web.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class SlotCsvDto {
    @CsvBindByName(column = "Bezeichnung", required = true)
    public String bezeichnung;

    @CsvBindByName(column = "Tag", required = true)
    public String tag;

    @CsvBindByName(column = "Beginn", required = true)
    public String beginntUm;

    @CsvBindByName(column = "Ende", required = true)
    public String endetUm;
}
