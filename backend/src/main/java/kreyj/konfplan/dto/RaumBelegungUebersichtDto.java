package kreyj.konfplan.dto;

public class RaumBelegungUebersichtDto {
    public Long slotId;
    public String slotZeit;
    public Long raumId;
    public String raumName;
    public String vortragTitel;
    public String referentName;
    public String vortragTyp; // "WAHL", "PFLICHT", "FREI"

    public RaumBelegungUebersichtDto(Long slotId, String slotZeit, Long raumId, String raumName, String vortragTitel, String referentName, String vortragTyp) {
        this.slotId = slotId;
        this.slotZeit = slotZeit;
        this.raumId = raumId;
        this.raumName = raumName;
        this.vortragTitel = vortragTitel;
        this.referentName = referentName;
        this.vortragTyp = vortragTyp;
    }

    public String getSlotZeit() {
        return slotZeit;
    }
}
