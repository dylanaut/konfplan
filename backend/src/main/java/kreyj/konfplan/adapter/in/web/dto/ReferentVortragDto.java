package kreyj.konfplan.adapter.in.web.dto;

import lombok.Getter;

import java.util.List;

public class ReferentVortragDto {
    public String vortragTitel;
    @Getter
    public String slotZeit;
    public String raumName;
    public String gebaeudeName;
    public List<TeilnehmerDto> teilnehmer;

    public ReferentVortragDto(String vortragTitel, String slotZeit, String raumName, String gebaeudeName, List<TeilnehmerDto> teilnehmer) {
        this.vortragTitel = vortragTitel;
        this.slotZeit = slotZeit;
        this.raumName = raumName;
        this.gebaeudeName = gebaeudeName;
        this.teilnehmer = teilnehmer;
    }
}
