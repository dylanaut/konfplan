package kreyj.konfplan.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Protokoll;
import kreyj.konfplan.persistence.ProtokollKategorie;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class ProtokollDto extends AbstractIdDto {
    public LocalDateTime zeitpunkt;
    public String akteur;
    public ProtokollKategorie kategorie;
    public String ereignis;
    public String details;
    public Long referenzId;
}
