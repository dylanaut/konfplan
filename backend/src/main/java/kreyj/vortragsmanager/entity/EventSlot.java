package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import kreyj.vortragsmanager.entity.converter.LocalDateTimeConverter;

import java.time.LocalDateTime;

@Entity
public class EventSlot extends SqliteEntity {
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime startTime;
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime endTime;
    public String description;

    @Version
    public Long version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER")
    public Veranstaltung veranstaltung;
}
