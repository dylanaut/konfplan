package kreyj.konfplan.presentation.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranstaltung;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@RegisterForReflection
@NoArgsConstructor
public class VortragDto extends AbstractVersionedDto {
    public Long id;
    public String titel;
    public String inhalt;
    public String ausstattung;
    public boolean istPflicht;
    public boolean wiederholbar;
    public Set<Long> verfuegbareSlotIds = new HashSet<>();

    public String pflichtGruppe;
    public Long pflichtSlotId;
    public Long pflichtRaumId;
    public int maxWiederholungen;
    public Long veranstaltungId;
    public String veranstaltungName;
    public Long referentId;
    public String referentName;
    public String referentOrganisation;

    // Konstruktor für Pflichtvortrag
    public VortragDto(String titel, Referent referent, String pflichtGruppe, Raum pflichtRaum, Slot pflichtSlot, Veranstaltung veranstaltung) {
        this(titel, referent.getId(), pflichtGruppe, pflichtRaum.getId(), pflichtSlot.getId(), veranstaltung.getId());
    }

    // Konstruktor für Pflichtvortrag mit IDs
    public VortragDto(String titel, Long referentId, String pflichtGruppe, Long pflichtRaumId, Long pflichtSlotId, Long veranstaltungId) {
        this(titel, null, referentId, pflichtGruppe, pflichtRaumId, pflichtSlotId, veranstaltungId);
    }

    public VortragDto(String titel, String inhalt, Long referentId, String pflichtGruppe, Long pflichtRaumId, Long pflichtSlotId,
                      Long veranstaltungId) {
        this(true, titel, inhalt, referentId, veranstaltungId);

        Objects.requireNonNull(pflichtGruppe, "Pflichtgruppe darf nicht null sein");
        Objects.requireNonNull(pflichtRaumId, "PflichtraumId darf nicht null sein");
        Objects.requireNonNull(pflichtSlotId, "PflichtslotId darf nicht null sein");

        this.pflichtGruppe = pflichtGruppe;
        this.pflichtRaumId = pflichtRaumId;
        this.pflichtSlotId = pflichtSlotId;
    }

    public VortragDto(boolean istPflicht, String titel, String inhalt, Long referentId, Long veranstaltungId) {
        this(istPflicht, titel, inhalt, null, referentId, veranstaltungId);
    }

    public VortragDto(boolean istPflicht, String titel, String inhalt, String ausstattung, Long referentId,
                      Long veranstaltungId) {
        Objects.requireNonNull(titel, "Titel darf nicht null sein");
        Objects.requireNonNull(veranstaltungId, "VeranstaltungId darf nicht null sein");
        Objects.requireNonNull(referentId, "ReferentId darf nicht null sein");

        this.istPflicht = istPflicht;
        this.titel = titel;
        this.inhalt = inhalt;
        this.ausstattung = ausstattung;
        this.referentId = referentId;
        this.veranstaltungId = veranstaltungId;
    }
}