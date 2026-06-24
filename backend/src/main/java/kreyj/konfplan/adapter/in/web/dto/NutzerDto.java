package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

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
    public Set<String> gruppen;
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
                                       Set<String> gruppen, List<VortragPrioDto> prioritaeten) {
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
}
