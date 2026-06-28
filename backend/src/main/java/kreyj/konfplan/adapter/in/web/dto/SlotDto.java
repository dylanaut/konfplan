package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import kreyj.konfplan.persistence.Slot;

import java.time.LocalDateTime;

import static kreyj.konfplan.util.DateHelper.DAY_FORMATTER;
import static kreyj.konfplan.util.DateHelper.HOUR_FORMATTER;

@RegisterForReflection
public class SlotDto extends AbstractVersionedDto {
    public String description;

    public LocalDateTime startTime;

    public LocalDateTime endTime;

    public Long veranstaltungId;


    public String tag() {
        return DAY_FORMATTER.format(startTime);
    }


    public String start() {
        return HOUR_FORMATTER.format(startTime);
    }


    public String ende() {
        return HOUR_FORMATTER.format(endTime);
    }


    public String zeitraum() {
        return start() + " - " + ende();
    }


    public String zeitraumTag() {
        return tag() + ", " + start() + " - " + ende();
    }

    // -------------------------------------------------------------------
    // Mapper methods
    // -------------------------------------------------------------------


    public static SlotDto from(Slot slot) {
        SlotDto dto = new SlotDto();

        dto.id = slot.getId();
        dto.version = slot.getVersion();
        dto.description = slot.getDescription();
        dto.startTime = slot.getStartTime();
        dto.endTime = slot.getEndTime();
        dto.veranstaltungId = slot.getVeranstaltung().getId();

        return dto;
    }

}
