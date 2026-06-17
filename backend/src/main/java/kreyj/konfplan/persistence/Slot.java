package kreyj.konfplan.persistence;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

import static kreyj.konfplan.util.DateHelper.DAY_FORMATTER;
import static kreyj.konfplan.util.DateHelper.HOUR_FORMATTER;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Slot extends VersionedEntity {
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime startTime;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime endTime;
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "veranstaltung_id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    Veranstaltung veranstaltung;


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------


    public Slot(String description, LocalDateTime startTime, LocalDateTime endTime, Veranstaltung veranstaltung) {
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;

        Objects.requireNonNull(veranstaltung);
        this.veranstaltung = veranstaltung;
    }


    public String tag() {
        return DAY_FORMATTER.format(startTime);
    }


    public String start() {
        return HOUR_FORMATTER.format(startTime);
    }


    public String ende() {
        return HOUR_FORMATTER.format(endTime);
    }


    public String getSlotZeit() {
        return start() + " - " + ende();
    }
}