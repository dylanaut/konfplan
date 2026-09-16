package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.util.StringHelper;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
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
    /** Wie {@link #gruppenwerte}, aber nach Gruppenkategorie-Name gruppiert (siehe #690) - für
     * Reports/UIs, die je Gruppenkategorie eine eigene, sortier- und filterbare Spalte statt
     * einer einzigen flachen Gruppen-Liste anzeigen sollen. */
    public Map<String, List<String>> gruppenwerteByKategorie;


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
        Map<String, List<String>> gruppenwerteByKategorie = tn.getGruppenwerte().stream()
            .collect(Collectors.groupingBy(w -> w.getGruppenkategorie().getName(),
                Collectors.mapping(GruppenkategorieWert::getWert, Collectors.toList())));
        return new TeilnehmerDto(tn.getId(), tn.getFirstName(), tn.getLastName(), tn.getEmail(), tn.getGruppen(),
            gruppenwerte, gruppenwerteByKategorie);
    }
}
