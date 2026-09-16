package kreyj.konfplan.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.StringHelper;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@RegisterForReflection
@NoArgsConstructor
public class NutzerDto extends AbstractVersionedDto {
    public String loginName;
    public String email;
    public String firstName;
    public String lastName;
    public String role;
    public boolean isActive;
    public List<Long> veranstaltungIds;

    // Referent-spezifisch
    public String jobRole;
    public String organisation;

    // Teilnehmer-spezifisch
    public List<String> gruppen;
    /** Werte aus dem strukturierten Gruppenkategorien-Modell (siehe #690), gruppiert nach
     * Kategorie-Name - für die Anzeige als separate Spalten im Organisator-Dashboard. */
    public Map<String, List<String>> gruppenwerteByKategorie;
    public List<VortragPrioDto> prioritaeten;
    public Set<Neigung> neigungen;


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



    @JsonIgnore
    public String getFullname() {
        return StringHelper.fullname(firstName, lastName);
    }

    // -------------------------------------------------------------------
    // Mapper methods
    // -------------------------------------------------------------------


    public static NutzerDto from(Nutzer u) {
        NutzerDto dto = new NutzerDto();
        dto.id = u.getId();
        dto.version = u.getVersion();
        dto.loginName = u.getLoginName();
        dto.email = u.getEmail();
        dto.firstName = u.getFirstName();
        dto.lastName = u.getLastName();
        dto.role = u.getRole();
        dto.isActive = u.isActive();
        Set<Veranstaltung> veranstaltungen = u.getVeranstaltungen();
        dto.veranstaltungIds = null != veranstaltungen ? veranstaltungen.stream().map(IdEntity::getId).toList() : emptyList();

        if (u instanceof Referent r) {
            dto.jobRole = r.getJobRole();
            dto.organisation = r.getOrganisation();
        } else if (u instanceof Teilnehmer tn) {
            dto.gruppen = tn.getGruppen().stream().sorted(StringHelper.NUM_OR_ALPHA_COMPARATOR).toList();
            dto.gruppenwerteByKategorie = tn.getGruppenwerte().stream()
                .collect(Collectors.groupingBy(w -> w.getGruppenkategorie().getName(),
                    Collectors.mapping(GruppenkategorieWert::getWert, Collectors.toList())));
            dto.gruppenwerteByKategorie.values().forEach(werte -> werte.sort(StringHelper.NUM_OR_ALPHA_COMPARATOR));
            dto.neigungen = tn.getNeigungen();
            Set<Prioritaet> tnPrioritaeten = tn.getPrioritaeten();
            List<VortragPrioDto> mappedPrios = tnPrioritaeten.stream()
                .filter(p -> p.getPrioWert() > 0)
                .map(VortragPrioDto::from)
                .toList();
            if (!mappedPrios.isEmpty()) {
                dto.prioritaeten = mappedPrios;
            }
        } else if (u instanceof Betrachter b) {
            dto.gruppenwerteByKategorie = b.getGruppenwerte().stream()
                .collect(Collectors.groupingBy(w -> w.getGruppenkategorie().getName(),
                    Collectors.mapping(GruppenkategorieWert::getWert, Collectors.toList())));
            dto.gruppenwerteByKategorie.values().forEach(werte -> werte.sort(StringHelper.NUM_OR_ALPHA_COMPARATOR));
        }
        return dto;
    }
}
