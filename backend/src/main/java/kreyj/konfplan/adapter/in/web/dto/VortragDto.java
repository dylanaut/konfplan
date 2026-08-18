package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.AbschlussTyp;
import kreyj.konfplan.persistence.Pflichtvortrag;
import kreyj.konfplan.persistence.Raum;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Slot;
import kreyj.konfplan.persistence.Veranlagung;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Vortrag;
import kreyj.konfplan.persistence.VortragVerfuegbarkeit;
import kreyj.konfplan.persistence.Wahlvortrag;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static kreyj.konfplan.persistence.VortragVerfuegbarkeitId.vvId;

@RegisterForReflection
@NoArgsConstructor
public class VortragDto extends AbstractVersionedDto {
    public String titel;
    public String inhalt;
    public String ausstattung;
    public AbschlussTyp abschluss;
    public String abschlussName;
    public Set<Veranlagung> veranlagungen = new HashSet<>();
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
        this(true, titel, inhalt, null, referentId, veranstaltungId);

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


    // -------------------------------------------------------------------
    // Mapper methods
    // -------------------------------------------------------------------

    public static VortragDto from(Vortrag v) {
        VortragDto dto = new VortragDto();
        dto.id = v.getId();
        dto.version = v.getVersion();
        dto.titel = v.getTitel();
        dto.inhalt = v.getInhalt();
        dto.ausstattung = v.getAusstattung();
        dto.abschluss = v.getAbschluss();
        dto.abschlussName = v.getAbschluss() != null ? v.getAbschluss().getName() : null;
        dto.veranstaltungId = v.getVeranstaltung().getId();
        dto.veranstaltungName = v.getVeranstaltung().getName();

        dto.referentId = v.getReferent().getId();
        dto.referentName = v.getReferent().getFullName();
        dto.referentOrganisation = v.getReferent().getOrganisation();

        if (v instanceof Wahlvortrag wahlvortrag) {
            dto.wiederholbar = wahlvortrag.isWiederholbar();
            dto.maxWiederholungen = wahlvortrag.getMaxWiederholungen();
            dto.veranlagungen = wahlvortrag.getVeranlagungen();
            VortragVerfuegbarkeit vv = VortragVerfuegbarkeit.findById(vvId(
                    wahlvortrag, wahlvortrag.getVeranstaltung()));
            if (null == vv) {
                dto.verfuegbareSlotIds = Slot.<Slot>find("veranstaltung", v.getVeranstaltung())
                        .stream()
                        .map(Slot::getId)
                        .collect(Collectors.toSet());
            } else {
                dto.verfuegbareSlotIds = vv.getVerfuegbareSlotIds();
            }
        } else if (v instanceof Pflichtvortrag pflichtvortrag) {
            dto.istPflicht = true;
            dto.pflichtGruppe = pflichtvortrag.getPflichtgruppe();
            dto.pflichtRaumId = pflichtvortrag.getPflichtraum().getId();

            Slot pflichtslot = pflichtvortrag.getPflichtslot();
            if (pflichtslot != null) {
                dto.pflichtSlotId = pflichtslot.getId();
                dto.verfuegbareSlotIds = Set.of(pflichtslot.getId());
            }
        }

        return dto;
    }


    public static Vortrag mapDtoToVortrag(VortragDto dto) {
        Vortrag vortrag = dto.istPflicht ? new Pflichtvortrag() : new Wahlvortrag();

        vortrag.setId(dto.id);
        vortrag.setVersion(dto.version);
        vortrag.setTitel(dto.titel);
        vortrag.setInhalt(dto.inhalt);
        vortrag.setAusstattung(dto.ausstattung);
        vortrag.setAbschluss(dto.abschluss);
        vortrag.setVeranstaltung(Veranstaltung.findById(dto.veranstaltungId));
        vortrag.setReferent(Referent.findById(dto.referentId));
        if (vortrag instanceof Wahlvortrag wahlvortrag) {
            wahlvortrag.setWiederholbar(dto.wiederholbar);
            wahlvortrag.setMaxWiederholungen(dto.maxWiederholungen);
            wahlvortrag.setVeranlagungen(dto.veranlagungen);
        } else {
            Pflichtvortrag pflichtvortrag = (Pflichtvortrag) vortrag;
            pflichtvortrag.updatePflichtgruppe(dto.pflichtGruppe);
        }

        return vortrag;

    }
}
