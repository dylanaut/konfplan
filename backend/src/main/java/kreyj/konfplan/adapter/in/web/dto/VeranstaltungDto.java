package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.util.StringHelper;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static kreyj.konfplan.util.DateHelper.DAY_FORMATTER;
import static kreyj.konfplan.util.DateHelper.HOUR_FORMATTER;

@RegisterForReflection
@Getter
@Setter
public class VeranstaltungDto extends AbstractVersionedDto {
    private String name;
    private LocalDateTime beginntAm;
    private LocalDateTime endetAm;
    private LocalDateTime deadlineReferenten;
    private LocalDateTime deadlineTeilnehmer;

    private String logo;
    private String logo_link;
    private Integer maxPrioritaeten;
    private List<Long> organisatorIds = new ArrayList<>();
    private List<String> organisatorNamen = new ArrayList<>();
    private List<OrganisatorDto> organisatoren = new ArrayList<>();
    private List<GebaeudeSimpleDto> gebaeude = new ArrayList<>();
    private List<String> gruppen = new ArrayList<>();


    public List<String> getOrte() {
        return gebaeude.stream().map(gDto -> gDto.ort).toList();
    }


    public String startTag() {
        return DAY_FORMATTER.format(beginntAm);
    }


    public String endeTag() {
        return DAY_FORMATTER.format(endetAm);
    }


    public String startZeit() {
        return HOUR_FORMATTER.format(beginntAm);
    }


    public String endeZeit() {
        return HOUR_FORMATTER.format(endetAm);
    }


    public String zeitraum() {
        return startZeit() + " - " + endeZeit();
    }


    public String zeitraumTage() {
        if (LocalDate.from(beginntAm).isEqual(LocalDate.from(endetAm))) {
            return startTag() + ", " + startZeit() + " - " + endeZeit();
        } else {
            return startTag() + ", " + startZeit() + " - " + endeTag() + ", " + endeZeit();
        }
    }

    // -------------------------------------------------------------------
    // Mapping methods
    // -------------------------------------------------------------------

    public static VeranstaltungDto from(Veranstaltung v) {
        VeranstaltungDto dto = new VeranstaltungDto();
        dto.id = v.getId();
        dto.version = v.getVersion();

        dto.setName(v.getName());
        dto.setBeginntAm(v.getBeginntAm());
        dto.setEndetAm(v.getEndetAm());
        dto.setDeadlineReferenten(v.getDeadlineReferenten());
        dto.setDeadlineTeilnehmer(v.getDeadlineTeilnehmer());
        dto.setLogo(v.getLogo());
        dto.setLogo_link(v.getLogo_link());
        dto.setMaxPrioritaeten(v.getMaxPrioritaeten());

        // Organisatoren filtern und hinzufügen
        if (v.getNutzer() != null) {
            v.getNutzer().stream()
                .filter(u -> u instanceof Admin)
                .forEach(u -> {
                    dto.getOrganisatorIds().add(u.getId());
                    dto.getOrganisatorNamen().add(u.getFullName());
                    dto.getOrganisatoren().add(OrganisatorDto.from((Admin) u));
                });
        }

        dto.setGebaeude(v.getGebaeude().stream().map(GebaeudeSimpleDto::from).toList());
        dto.setGruppen(v.getGruppen().stream().sorted(StringHelper.NUM_OR_ALPHA_COMPARATOR).toList());

        return dto;
    }
}
