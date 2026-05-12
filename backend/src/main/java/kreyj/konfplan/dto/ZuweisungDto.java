package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class ZuweisungDto {
    public Long id;
    public String teilnehmerName;
    public String vortragTitel;
    public String slotZeit;
    public String raumName;
    public String gebaeudeName;

    public ZuweisungDto() {}

    public ZuweisungDto(Long id, String teilnehmerName, String vortragTitel, String slotZeit, String raumName, String gebaeudeName) {
        this.id = id;
        this.teilnehmerName = teilnehmerName;
        this.vortragTitel = vortragTitel;
        this.slotZeit = slotZeit;
        this.raumName = raumName;
        this.gebaeudeName = gebaeudeName;
    }

    public String getSlotZeit() {
        return slotZeit;
    }
}
