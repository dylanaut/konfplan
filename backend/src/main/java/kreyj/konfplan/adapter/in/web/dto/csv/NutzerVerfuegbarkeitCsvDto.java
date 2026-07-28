package kreyj.konfplan.adapter.in.web.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class NutzerVerfuegbarkeitCsvDto {

    @CsvBindByName(column = "loginName", required = true)
    public String loginName;

    @CsvBindByName(column = "verfuegbare_slots")
    public String verfuegbareSlots;
}
