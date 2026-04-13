package kreyj.vortragsmanager.dto;

import kreyj.vortragsmanager.entity.Gebaeude;

public class GebaeudeSimpleDto {
    public Long id;

    public String name;

    public Gebaeude.Gebaeudetyp typ;

    public String ort;

    public GebaeudeSimpleDto() {
    }
}
