package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.util.StringHelper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class TeilnehmerDto {
    public Long id;
    public String firstName;
    public String lastName;
    public Set<String> gruppen;


    public String getFullname() {
        return StringHelper.fullname(firstName, lastName);
    }


    public String gName() {
        return String.format("%s (%s)", getFullname(),
            gruppen.stream().sorted().collect(Collectors.joining(",")));
    }

    // -------------------------------------------------------------------
    // Override methods
    // -------------------------------------------------------------------


    @Override
    public String toString() {
        return getFullname();
    }


    // -------------------------------------------------------------------
    // Mapper methods
    // -------------------------------------------------------------------


    public static TeilnehmerDto from(Teilnehmer tn) {
        return new TeilnehmerDto(tn.getId(), tn.getFirstName(), tn.getLastName(), tn.getGruppen());
    }
}
