package kreyj.konfplan.dto;

import java.util.List;

public class ReferentVortragDetailDto {
    public String vortragTitel;
    public String slotZeit;
    public String raumName;
    public String gebaeudeName;
    public List<TeilnehmerSimpleDto> teilnehmer;

    public ReferentVortragDetailDto(String vortragTitel, String slotZeit, String raumName, String gebaeudeName, List<TeilnehmerSimpleDto> teilnehmer) {
        this.vortragTitel = vortragTitel;
        this.slotZeit = slotZeit;
        this.raumName = raumName;
        this.gebaeudeName = gebaeudeName;
        this.teilnehmer = teilnehmer;
    }

    public String getSlotZeit() {
        return slotZeit;
    }
}
