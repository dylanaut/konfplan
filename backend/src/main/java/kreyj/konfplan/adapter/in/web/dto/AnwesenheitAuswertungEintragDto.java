package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class AnwesenheitAuswertungEintragDto {
    public Long slotId;
    public String slotZeit;
    public Long raumId;
    public String raumName;
    public String vortragTitel;
    public List<String> warDa;
    public List<String> fehlte;
    public List<String> unangemeldet;

    public AnwesenheitAuswertungEintragDto(Long slotId, String slotZeit, Long raumId, String raumName, String vortragTitel,
                                            List<String> warDa, List<String> fehlte, List<String> unangemeldet) {
        this.slotId = slotId;
        this.slotZeit = slotZeit;
        this.raumId = raumId;
        this.raumName = raumName;
        this.vortragTitel = vortragTitel;
        this.warDa = warDa;
        this.fehlte = fehlte;
        this.unangemeldet = unangemeldet;
    }
}
