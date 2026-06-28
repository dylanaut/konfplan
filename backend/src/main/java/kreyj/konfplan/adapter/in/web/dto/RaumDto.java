package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Raum;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RaumDto extends AbstractVersionedDto {
    public String name;
    public int kapazitaet;
    public String etage;
    public Long gebaeudeId;
    public String gebaeudeName;


    // -------------------------------------------------------------------
    // Mapper methods
    // -------------------------------------------------------------------

    public static RaumDto from(Raum raum) {
        RaumDto dto = new RaumDto();

        dto.id = raum.getId();
        dto.version = raum.getVersion();
        dto.name = raum.getName();
        dto.kapazitaet = raum.getKapazitaet();
        dto.etage = raum.getEtage();

        dto.gebaeudeId = raum.getGebaeude().getId();
        dto.gebaeudeName = raum.getGebaeude().getName();

        return dto;
    }
}
