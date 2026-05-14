package kreyj.konfplan.persistence;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;

import java.time.LocalDateTime;

@Entity
public class EventSlot extends VersionedEntity {
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime startTime;
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime endTime;
    public String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "veranstaltung_id")
    public Veranstaltung veranstaltung;

    public EventSlot() {
    }

    public EventSlot(String description, LocalDateTime startTime, LocalDateTime endTime, Veranstaltung veranstaltung) {
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.veranstaltung = veranstaltung;
    }
}
