package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RegisterForReflection
@NoArgsConstructor
public class VortragDto extends AbstractVersionedDto {
    public Long id;
    public String titel;
    public String inhalt;
    public boolean istPflicht;
    public boolean wiederholbar;
    public List<Long> verfuegbareSlotIds = new ArrayList<>();

    public String pflichtGruppe;
    public Long pflichtSlotId;
    public Long pflichtRaumId;
    public int maxWiederholungen;
    public Long veranstaltungId;
    public String veranstaltungName;
    public Long referentId;
    public String referentName;
    public String referentOrganisation;

    public VortragDto(String titel, Referent referent, String gruppe, Raum raum, Slot slot, Veranstaltung veranstaltung) {
        this(titel, referent.getId(), gruppe, raum.getId(), slot.getId(), veranstaltung.getId());
    }

    public VortragDto(String titel, Long referentId, String gruppe, Long raumId, Long slotId, Long veranstaltungId) {
        this.istPflicht = true;
        this.titel = titel;

        this.veranstaltungId = veranstaltungId;
        this.referentId = referentId;

        this.pflichtGruppe = gruppe;
        this.pflichtRaumId = raumId;
        this.pflichtSlotId = slotId;
    }

    public VortragDto(boolean istPflicht, String titel, String inhalt, Long referentId, Long veranstaltungId) {
        this.istPflicht = istPflicht;
        this.titel = titel;
        this.inhalt = inhalt;
        this.referentId = referentId;
        this.veranstaltungId = veranstaltungId;
    }
}
