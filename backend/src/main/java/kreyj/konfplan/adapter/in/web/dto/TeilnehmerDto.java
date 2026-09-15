package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.GruppenkategorieWert;
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
    public String email;
    public Set<String> gruppen;
    /** Werte aus dem strukturierten Gruppenkategorien-Modell (siehe #690), als Wert-Strings für
     * den Übergang neben {@link #gruppen} - siehe {@link #istInGruppe(String)}. */
    public Set<String> gruppenwerte;


    public String getFullname() {
        return StringHelper.fullname(firstName, lastName);
    }


    /**
     * Prüft die Gruppenmitgliedschaft gegen BEIDE Modelle (siehe {@link Teilnehmer#istInGruppe}
     * für das Pendant auf der Entity-Ebene).
     */
    public boolean istInGruppe(String gruppe) {
        return null != gruppe && (gruppen.contains(gruppe) || gruppenwerte.contains(gruppe));
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
        Set<String> gruppenwerte = tn.getGruppenwerte().stream().map(GruppenkategorieWert::getWert).collect(Collectors.toSet());
        return new TeilnehmerDto(tn.getId(), tn.getFirstName(), tn.getLastName(), tn.getEmail(), tn.getGruppen(), gruppenwerte);
    }
}
