package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.StringHelper;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

@RegisterForReflection
@NoArgsConstructor
public class NutzerDto extends AbstractVersionedDto {
    public String email;
    public String firstName;
    public String lastName;
    public String role;
    public boolean isActive;
    public List<Long> veranstaltungIds;

    // Referent-spezifisch
    public String biography;
    public String jobRole;
    public String organisation;
    public String slogan;

    // Teilnehmer-spezifisch
    public List<String> gruppen;
    public List<VortragPrioDto> prioritaeten;

    public NutzerDto(String role, String email, String firstName, String lastName, boolean active) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.isActive = active;
    }

    public static NutzerDto teilnehmer(String email, String firstName, String lastName) {
        return new NutzerDto("TEILNEHMER", email, firstName, lastName, true);
    }

    public static NutzerDto teilnehmer(String email, String firstName, String lastName,
                                       List<String> gruppen, List<VortragPrioDto> prioritaeten) {
        NutzerDto teilnehmer = teilnehmer(email, firstName, lastName);

        teilnehmer.gruppen = gruppen;
        teilnehmer.prioritaeten = prioritaeten;

        return teilnehmer;
    }

    public static NutzerDto referent(String email, String firstName, String lastName) {
        return new NutzerDto("REFERENT", email, firstName, lastName, true);
    }

    public static NutzerDto referent(String email, String firstName, String lastName,
                                     String biography, String jobRole, String organisation, String slogan) {
        NutzerDto referent = referent(email, firstName, lastName);

        referent.biography = biography;
        referent.jobRole = jobRole;
        referent.organisation = organisation;
        referent.slogan = slogan;

        return referent;
    }


    public String fullName() {
        if (StringUtils.isBlank(firstName)) {
            if (StringUtils.isBlank(lastName)) {
                return "NONAME";
            } else {
                return lastName;
            }
        } else if (StringUtils.isBlank(lastName)) {
            return firstName;
        } else {
            return firstName + " " + lastName;
        }
    }

    // -------------------------------------------------------------------
    // Mapper methods
    // -------------------------------------------------------------------

    public static NutzerDto from(Nutzer u) {
        NutzerDto dto = new NutzerDto();
        dto.id = u.getId();
        dto.version = u.getVersion();
        dto.email = u.getEmail();
        dto.firstName = u.getFirstName();
        dto.lastName = u.getLastName();
        dto.role = u.getRole();
        dto.isActive = u.isActive();
        Set<Veranstaltung> veranstaltungen = u.getVeranstaltungen();
        dto.veranstaltungIds = null != veranstaltungen ? veranstaltungen.stream().map(IdEntity::getId).toList() : emptyList();

        if (u instanceof Referent r) {
            dto.biography = r.getBiography();
            dto.jobRole = r.getJobRole();
            dto.organisation = r.getOrganisation();
            dto.slogan = r.getSlogan();
        } else if (u instanceof Teilnehmer tn) {
            dto.gruppen = tn.getGruppen().stream().sorted(StringHelper.NUM_OR_ALPHA_COMPARATOR).toList();
            Set<Prioritaet> tnPrioritaeten = tn.getPrioritaeten();
            List<VortragPrioDto> mappedPrios = tnPrioritaeten.stream()
                .filter(p -> p.getPrioWert() > 0)
                .map(VortragPrioDto::from)
                .toList();
            if (!mappedPrios.isEmpty()) {
                dto.prioritaeten = mappedPrios;
            }
        }
        return dto;
    }
}
