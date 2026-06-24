package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private List<Long> organisatorIds = new ArrayList<>();
    private List<String> organisatorNamen = new ArrayList<>();
    private List<GebaeudeSimpleDto> gebaeude = new ArrayList<>();
    private Set<String> gruppen = new HashSet<>();


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
}
