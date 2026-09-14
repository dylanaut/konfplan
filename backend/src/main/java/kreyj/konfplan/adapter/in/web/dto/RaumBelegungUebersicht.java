package kreyj.konfplan.adapter.in.web.dto;

import lombok.Getter;

import java.util.List;

public class RaumBelegungUebersicht {
    public static final String VORTRAG_TYP_FREI = "FREI";
    public static final String VORTRAG_TITEL_FREI = "Frei";

    public final Long slotId;
    @Getter
    public String slotZeit;
    public final Long raumId;
    public final String raumName;
    public final String raumGebaeudeKuerzel;
    @Getter
    public String vortragTitel;
    public final String referentName;
    public final String vortragTyp; // "WAHL", "PFLICHT", "FREI"
    @Getter
    public List<String> teilnehmerNamen;
    public List<String> teilnehmerGruppen;
    public final Integer kapazitaet;

    public RaumBelegungUebersicht(Long slotId, String slotZeit, Long raumId, String raumName, String raumGebaeudeKuerzel,
                                  String vortragTitel, String referentName, String vortragTyp,
                                  List<String> teilnehmerNamen, List<String> teilnehmerGruppen, Integer kapazitaet) {
        this.slotId = slotId;
        this.slotZeit = slotZeit;
        this.raumId = raumId;
        this.raumName = raumName;
        this.raumGebaeudeKuerzel = raumGebaeudeKuerzel;
        this.vortragTitel = vortragTitel;
        this.referentName = referentName;
        this.vortragTyp = vortragTyp;
        this.teilnehmerNamen = teilnehmerNamen;
        this.teilnehmerGruppen = teilnehmerGruppen;
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
