package kreyj.vortragsmanager.dto;

import com.opencsv.bean.CsvBindByName;

/**
 * DTO für den kompakten Gebäude-Import inkl. Räume.
 * Format für Räume: Name:Kapazität:Etage|Name:Kapazität:Etage
 */
public class GebaeudeImportCsvDto {
    @CsvBindByName(column = "Name", required = true)
    public String name;

    @CsvBindByName(column = "Typ", required = true)
    public String typ;

    @CsvBindByName(column = "Strasse", required = true)
    public String strasse;

    @CsvBindByName(column = "Hausnummer")
    public String hausnummer;

    @CsvBindByName(column = "PLZ", required = true)
    public String postleitzahl;

    @CsvBindByName(column = "Ort", required = true)
    public String ort;

    @CsvBindByName(column = "Raeume")
    public String raeumeRaw; // Format: "Aula:200:EG|R101:30:1.OG"
}
