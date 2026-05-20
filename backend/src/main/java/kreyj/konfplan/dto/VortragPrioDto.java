package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Prioritaet;

@RegisterForReflection
public class VortragPrioDto {
    public Long vortragId;
    public int prioWert;

    public static VortragPrioDto from(Prioritaet p) {
        VortragPrioDto dto = new VortragPrioDto();
        dto.vortragId = p.getVortrag().getId();
        dto.prioWert = p.getPrioWert();
        return dto;
    }
}
