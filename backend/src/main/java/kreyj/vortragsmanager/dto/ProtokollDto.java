package kreyj.vortragsmanager.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.vortragsmanager.entity.Protokoll;
import kreyj.vortragsmanager.entity.ProtokollKategorie;

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
        this.id = protokoll.id;
        this.zeitpunkt = protokoll.zeitpunkt;
        this.akteur = protokoll.akteur;
        this.kategorie = protokoll.kategorie;
        this.ereignis = protokoll.ereignis;
        this.details = protokoll.details;
        this.referenzId = protokoll.referenzId;
    }
}
