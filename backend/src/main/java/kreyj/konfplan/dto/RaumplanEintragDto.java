package kreyj.konfplan.dto;

import java.util.List;

public class RaumplanEintragDto {
    public Long slotId;
    public String slotZeit;
    public String vortragTitel;
    public String referentName;
    public String vortragTyp;
    public int teilnehmerCount;
    public List<TeilnehmerSimpleDto> teilnehmer; // Für Anwesenheitsliste

    public RaumplanEintragDto(Long slotId, String slotZeit, String vortragTitel, String referentName, String vortragTyp, int teilnehmerCount, List<TeilnehmerSimpleDto> teilnehmer) {
        this.slotId = slotId;
        this.slotZeit = slotZeit;
        this.vortragTitel = vortragTitel;
        this.referentName = referentName;
        this.vortragTyp = vortragTyp;
        this.teilnehmerCount = teilnehmerCount;
        this.teilnehmer = teilnehmer;
    }

    public String getSlotZeit() {
        return slotZeit;
    }
}
