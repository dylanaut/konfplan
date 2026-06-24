package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Prioritaet;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@AllArgsConstructor
@NoArgsConstructor
public class VortragPrioDto {
    public Long vortragId;
    public int prioWert;

    public static VortragPrioDto from(Prioritaet p) {
        return new VortragPrioDto(p.getVortrag().getId(), p.getPrioWert());
    }
}
