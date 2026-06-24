package kreyj.konfplan.adapter.in.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

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
}
