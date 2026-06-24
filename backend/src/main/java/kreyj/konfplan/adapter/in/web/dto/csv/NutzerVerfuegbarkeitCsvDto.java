package kreyj.konfplan.adapter.in.web.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class NutzerVerfuegbarkeitCsvDto {

    @CsvBindByName(column = "email", required = true)
    public String email;

    @CsvBindByName(column = "verfuegbare_slots")
    public String verfuegbareSlots;
}
