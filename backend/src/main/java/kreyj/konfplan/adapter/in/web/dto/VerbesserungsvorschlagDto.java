package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Dringlichkeit;
import kreyj.konfplan.persistence.Verbesserungsvorschlag;
import kreyj.konfplan.persistence.VorschlagStatus;

import java.time.LocalDateTime;

@RegisterForReflection
public class VerbesserungsvorschlagDto extends AbstractVersionedDto {
    public String titel;
    public String beschreibung;
    public LocalDateTime erstelltAm;
    public String erstellerName;
    public String erstellerRolle;
    public VorschlagStatus status;
    public Dringlichkeit dringlichkeit;
    public String release;

    public static VerbesserungsvorschlagDto from(Verbesserungsvorschlag v) {
        VerbesserungsvorschlagDto dto = new VerbesserungsvorschlagDto();
        dto.id = v.getId();
        dto.version = v.getVersion();
        dto.titel = v.getTitel();
        dto.beschreibung = v.getBeschreibung();
        dto.erstelltAm = v.getErstelltAm();
        dto.status = v.getStatus();
        dto.dringlichkeit = v.getDringlichkeit();
        dto.release = v.getRelease();
        if (null != v.getErsteller()) {
            dto.erstellerName = v.getErsteller().getFullName();
            dto.erstellerRolle = v.getErsteller().getRole();
        }
        return dto;
    }
}
