package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class EventSlot extends SqliteEntity {
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER")
    public Veranstaltung veranstaltung;
}
