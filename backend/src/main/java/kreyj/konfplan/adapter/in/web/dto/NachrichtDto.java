package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Nachricht;
import kreyj.konfplan.persistence.NachrichtKategorie;

import java.time.LocalDateTime;

@RegisterForReflection
public class NachrichtDto {
    public Long id;
    public String absender;
    public String titel;
    public String inhalt;
    public NachrichtKategorie kategorie;
    public LocalDateTime erstelltAm;
    public LocalDateTime gelesenAm;
    public Long veranstaltungId;

    public static NachrichtDto from(Nachricht n) {
        NachrichtDto dto = new NachrichtDto();
        dto.id = n.getId();
        dto.absender = n.getAbsender();
        dto.titel = n.getTitel();
        dto.inhalt = n.getInhalt();
        dto.kategorie = n.getKategorie();
        dto.erstelltAm = n.getErstelltAm();
        dto.gelesenAm = n.getGelesenAm();
        dto.veranstaltungId = n.getVeranstaltungId();
        return dto;
    }
}
