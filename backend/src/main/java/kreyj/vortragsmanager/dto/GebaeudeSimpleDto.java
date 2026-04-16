package kreyj.vortragsmanager.dto;

import kreyj.vortragsmanager.entity.Gebaeude;

import java.util.List;

public class GebaeudeSimpleDto extends VersionedDto {
    public String name;

    public String strasse;

    public String hausnummer;

    public String postleitzahl;

    public String ort;

    public Gebaeude.Gebaeudetyp typ;

    public List<RaumDto> raeume;

    public GebaeudeSimpleDto() {
    }
}
