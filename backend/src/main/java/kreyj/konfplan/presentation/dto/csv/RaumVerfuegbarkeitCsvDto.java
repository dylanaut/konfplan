package kreyj.konfplan.presentation.dto.csv;

import com.opencsv.bean.CsvBindByName;

public class RaumVerfuegbarkeitCsvDto {

    @CsvBindByName(column = "gebaeude", required = true)
    public String gebaeude;

    @CsvBindByName(column = "raum", required = true)
    public String raum;

    @CsvBindByName(column = "verfuegbare_slots")
    public String verfuegbareSlots;
}
