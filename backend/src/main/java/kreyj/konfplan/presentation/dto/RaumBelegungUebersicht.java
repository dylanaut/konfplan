package kreyj.konfplan.presentation.dto;

import lombok.Getter;

import java.util.List;

public class RaumBelegungUebersicht {
    public static final String VORTRAG_TYP_FREI = "FREI";
    public static final String VORTRAG_TITEL_FREI = "Frei";

    public Long slotId;
    @Getter
    public String slotZeit;
    public Long raumId;
    public String raumName;
    @Getter
    public String vortragTitel;
    public String referentName;
    public String vortragTyp; // "WAHL", "PFLICHT", "FREI"
    @Getter
    public List<String> teilnehmerNamen;
    public Integer kapazitaet;

    public RaumBelegungUebersicht(Long slotId, String slotZeit, Long raumId, String raumName, String vortragTitel, String referentName, String vortragTyp, List<String> teilnehmerNamen, Integer kapazitaet) {
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

    @Override
    public String toString() {
        return slotZeit + '(' + slotId +
                ") @ " + raumName + '(' + raumId +
                "): titel='" + vortragTitel + '\'' +
                ", ref='" + referentName + '\'' +
                ", vortragTyp='" + vortragTyp + '\'' +
                ", tn=" + teilnehmerNamen +
                ", kapazitaet=" + kapazitaet;
    }
}
