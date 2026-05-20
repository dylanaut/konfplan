package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Protokoll;
import kreyj.konfplan.persistence.ProtokollKategorie;

import java.time.LocalDateTime;

@RegisterForReflection
public class ProtokollDto extends AbstractIdDto {
    public LocalDateTime zeitpunkt;
    public String akteur;
    public ProtokollKategorie kategorie;
    public String ereignis;
    public String details;
    public Long referenzId;

    public ProtokollDto() {
    }

    public ProtokollDto(Protokoll protokoll) {
        this.id = protokoll.getId();
        this.zeitpunkt = protokoll.getZeitpunkt();
        this.akteur = protokoll.getAkteur();
        this.kategorie = protokoll.getKategorie();
        this.ereignis = protokoll.getEreignis();
        this.details = protokoll.getDetails();
        this.referenzId = protokoll.getReferenzId();
    }
}
