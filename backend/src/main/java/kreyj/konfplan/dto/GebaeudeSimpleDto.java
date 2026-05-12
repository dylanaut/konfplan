package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Gebaeude;

import java.util.List;

@RegisterForReflection
public class GebaeudeSimpleDto extends AbstractVersionedDto {
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
