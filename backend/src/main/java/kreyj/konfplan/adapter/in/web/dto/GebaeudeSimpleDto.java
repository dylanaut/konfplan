package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Gebaeude;
import kreyj.konfplan.persistence.Gebaeudetyp;

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


    public static GebaeudeSimpleDto from(Gebaeude gebaeude) {
        GebaeudeSimpleDto dto = new GebaeudeSimpleDto();
        dto.id = gebaeude.getId();
        dto.version = gebaeude.getVersion();

        dto.name = gebaeude.getName();
        dto.strasse = gebaeude.getStrasse();
        dto.hausnummer = gebaeude.getHausnummer();
        dto.ort = gebaeude.getOrt();
        dto.postleitzahl = gebaeude.getPostleitzahl();
        dto.typ = gebaeude.getTyp();

        dto.raeume = gebaeude.getRaeume().stream()
            .map(RaumDto::from)
            .toList();

        return dto;
    }
}
