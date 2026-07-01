package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.hibernate.orm.panache.common.ProjectedFieldName;
import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Prioritaet;
import lombok.Getter;

@RegisterForReflection
@Getter
public class VortragPrioDto {
    public Long vortragId;

    public int prioWert;


    /**
     * notwendiger allArgs Konstruktor mit @ProjectedFieldName für Panache Query Projektion
     *
     * @param vortragId vortragId
     * @param prioWert  prioWert
     */
    public VortragPrioDto(@ProjectedFieldName("vortrag.id") Long vortragId, int prioWert) {
        this.vortragId = vortragId;
        this.prioWert = prioWert;
    }


    public static VortragPrioDto from(Prioritaet p) {
        return new VortragPrioDto(p.getVortrag().getId(), p.getPrioWert());
    }
}
