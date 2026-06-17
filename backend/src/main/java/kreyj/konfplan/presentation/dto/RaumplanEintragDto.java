package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class RaumplanEintragDto {
    public Long slotId;
    public String slotZeit;
    public String vortragTitel;
    public String referentName;
    public String vortragTyp;
    public List<TeilnehmerDto> teilnehmer;


    public int anzahlTeilnehmer() {
        return teilnehmer == null ? 0 : teilnehmer.size();
    }


    @Override
    public String toString() {
        return "RaumplanEintragDto{" +
                "slotId=" + slotId +
                ", titel='" + vortragTitel + '\'' +
                ", ref='" + referentName + '\'' +
                ", vortragTyp='" + vortragTyp + '\'' +
                ", teilnehmer=" + teilnehmer +
                '}';
    }
}

