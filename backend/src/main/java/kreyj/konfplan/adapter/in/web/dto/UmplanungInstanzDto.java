package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class UmplanungInstanzDto {
    public Long wahlvortragId;
    public int instanzIndex;
    public String vortragTitel;
    public String referentName;
    public Long slotId;
    public String slotZeit;
    public Long raumId;
    public String raumName;
    public int kapazitaet;
    public int belegteAnzahl;
    public boolean ausgefallen;
}
