package kreyj.vortragsmanager.dto;

import com.opencsv.bean.CsvBindByName;

public class GebaeudeRaeumeCsvDto {
    @CsvBindByName(column = "Name", required = true)
    public String name;

    @CsvBindByName(column = "Typ", required = true)
    public String typ;

    @CsvBindByName(column = "Strasse", required = true)
    public String strasse;

    @CsvBindByName(column = "Hausnummer")
    public String hausnummer;

    @CsvBindByName(column = "PLZ", required = true)
    public String plz;

    @CsvBindByName(column = "Ort", required = true)
    public String ort;

    @CsvBindByName(column = "Räume")
    public String raeumeRaw; // Format: "Name:Kap:Etage|Name:Kap:Etage"
}
