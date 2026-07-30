package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.hibernate.orm.panache.common.ProjectedFieldName;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;

@RegisterForReflection
@Getter
public class TeilnehmerVortragPrioDto {
    public Long teilnehmerId;
    public Long vortragId;
    public int prioWert;


    public TeilnehmerVortragPrioDto(@ProjectedFieldName("teilnehmer.id") Long teilnehmerId,
                                     @ProjectedFieldName("vortrag.id") Long vortragId, int prioWert) {
        this.teilnehmerId = teilnehmerId;
        this.vortragId = vortragId;
        this.prioWert = prioWert;
    }
}
