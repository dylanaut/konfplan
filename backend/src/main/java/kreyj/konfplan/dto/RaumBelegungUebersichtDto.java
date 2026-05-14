package kreyj.konfplan.dto;

import java.util.List;

public class RaumBelegungUebersichtDto {
    public Long slotId;
    public String slotZeit;
    public Long raumId;
    public String raumName;
    public String vortragTitel;
    public String referentName;
    public String vortragTyp; // "WAHL", "PFLICHT", "FREI"
    public List<String> teilnehmerNamen;
    public Integer kapazitaet;

    public RaumBelegungUebersichtDto(Long slotId, String slotZeit, Long raumId, String raumName, String vortragTitel, String referentName, String vortragTyp, List<String> teilnehmerNamen, Integer kapazitaet) {
        this.slotId = slotId;
        this.slotZeit = slotZeit;
        this.raumId = raumId;
        this.raumName = raumName;
        this.vortragTitel = vortragTitel;
        this.referentName = referentName;
        this.vortragTyp = vortragTyp;
        this.teilnehmerNamen = teilnehmerNamen;
        this.kapazitaet = kapazitaet;
    }

    public String getSlotZeit() {
        return slotZeit;
    }

    public String getVortragTitel() {
        return vortragTitel;
    }

    public List<String> getTeilnehmerNamen() {
        return teilnehmerNamen;
    }

    @Override
    public String toString() {
        return "RaumBelegungUebersichtDto{" +
                "slotId=" + slotId +
                ", slotZeit='" + slotZeit + '\'' +
                ", raumId=" + raumId +
                ", raumName='" + raumName + '\'' +
                ", vortragTitel='" + vortragTitel + '\'' +
                ", referentName='" + referentName + '\'' +
                ", vortragTyp='" + vortragTyp + '\'' +
                ", teilnehmerNamen=" + teilnehmerNamen +
                ", kapazitaet=" + kapazitaet +
                '}';
    }
}
