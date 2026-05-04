package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.vortragsmanager.entity.Prioritaet;
import kreyj.vortragsmanager.entity.Vortrag;

@RegisterForReflection
public class VortragPrioDto {
    public Long vortragId;
    public int prioWert;

    public static VortragPrioDto from(Prioritaet p) {
        VortragPrioDto dto = new VortragPrioDto();
        dto.vortragId = p.vortrag.id;
        dto.prioWert = p.prioWert;
        return dto;
    }
}
