package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class VortragBelegungDto {
    public String vortragTitel;
    public String referentName;
    public String slotZeit;
    public String raumName;
    public List<String> teilnehmerNamen;
    public int auslastung; // in Prozent
    public int kapazitaet;

    public VortragBelegungDto() {
    }
}
