package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Gebaeudetyp;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
public class GebaeudeSimpleDto extends AbstractVersionedDto {
    public String name;

    public String strasse;

    public String hausnummer;

    public String postleitzahl;

    public String ort;

    public Gebaeudetyp typ;

    public List<RaumDto> raeume;
}
