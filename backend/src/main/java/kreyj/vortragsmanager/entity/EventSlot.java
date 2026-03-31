package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import java.time.LocalDateTime;

@Entity

public class EventSlot extends SqliteEntity {
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public String description; // z.B. "Slot A"
}