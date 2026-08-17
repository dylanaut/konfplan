package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Veranlagung;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
public class VeranlagungDto {
    public String name;
    public String bezeichnung;
    public String beschreibung;

    public static VeranlagungDto from(Veranlagung veranlagung) {
        VeranlagungDto dto = new VeranlagungDto();
        dto.name = veranlagung.name();
        dto.bezeichnung = veranlagung.getBezeichnung();
        dto.beschreibung = veranlagung.getBeschreibung();
        return dto;
    }
}
