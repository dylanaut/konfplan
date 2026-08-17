package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Neigung;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
public class NeigungDto {
    public String name;
    public String bezeichnung;
    public String beschreibung;
    public String kategorie;
    public String kategorieBezeichnung;

    public static NeigungDto from(Neigung neigung) {
        NeigungDto dto = new NeigungDto();
        dto.name = neigung.name();
        dto.bezeichnung = neigung.getBezeichnung();
        dto.beschreibung = neigung.getBeschreibung();
        dto.kategorie = neigung.getKategorie().name();
        dto.kategorieBezeichnung = neigung.getKategorie().getBezeichnung();
        return dto;
    }
}
