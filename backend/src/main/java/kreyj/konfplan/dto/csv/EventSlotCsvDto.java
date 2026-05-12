package kreyj.konfplan.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class EventSlotCsvDto {
    @CsvBindByName(column = "Bezeichnung", required = true)
    public String description;

    @CsvBindByName(column = "Tag", required = true)
    public String day;

    @CsvBindByName(column = "Beginn", required = true)
    public String startTime;

    @CsvBindByName(column = "Ende", required = true)
    public String endTime;
}
