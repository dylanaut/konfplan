package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import kreyj.vortragsmanager.entity.converter.LocalDateTimeConverter;

import java.time.LocalDateTime;

@Entity
public class EventSlot extends VersionedEntity {
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime startTime;
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime endTime;
    public String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER")
    public Veranstaltung veranstaltung;
}
