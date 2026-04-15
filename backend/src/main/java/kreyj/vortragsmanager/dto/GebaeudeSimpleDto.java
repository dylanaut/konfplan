package kreyj.vortragsmanager.dto;

import kreyj.vortragsmanager.entity.Gebaeude;

public class GebaeudeSimpleDto {
    public Long id;

    public String name;

    public String strasse;

    public String hausnummer;

    public String postleitzahl;

    public String ort;

    public Gebaeude.Gebaeudetyp typ;

    public Long version;

    public GebaeudeSimpleDto() {
    }
}
