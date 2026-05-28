package kreyj.konfplan.presentation.dto.csv;

import com.opencsv.bean.CsvBindByName;

import java.util.Map;

public class TnPriosCsvDto {
    @CsvBindByName(column = "Teilnehmer E-Mail", required = true)
    private String teilnehmerEmail;

    @CsvBindByName(column = "Prioritäten", required = true)
    private Map<Long, Integer> prioritaeten;
}
