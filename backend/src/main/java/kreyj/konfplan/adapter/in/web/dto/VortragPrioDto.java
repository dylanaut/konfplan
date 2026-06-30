package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.hibernate.orm.panache.common.ProjectedFieldName;
import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Prioritaet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@RegisterForReflection
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class VortragPrioDto {

    @ProjectedFieldName("vortrag.id")
    public Long vortragId;

    @ProjectedFieldName("prioWert")
    public int prioWert;


    public static VortragPrioDto from(Prioritaet p) {
        return new VortragPrioDto(p.getVortrag().getId(), p.getPrioWert());
    }
}
