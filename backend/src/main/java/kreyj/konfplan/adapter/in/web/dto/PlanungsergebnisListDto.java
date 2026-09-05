package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
@SuppressWarnings("unused")
public class PlanungsergebnisListDto {
    public Long id;
    public String ersteller;
    public LocalDateTime erstelltAm;
    public int guete;
    public boolean publiziert;


    public PlanungsergebnisListDto() {
    }


    public PlanungsergebnisListDto(Long id, String ersteller, LocalDateTime erstelltAm, int guete, boolean publiziert) {
        this.id = id;
        this.ersteller = ersteller;
        this.erstelltAm = erstelltAm;
        this.guete = guete;
        this.publiziert = publiziert;
    }
}
