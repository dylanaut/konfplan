package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Priority extends SqliteEntity {
    @Version  // opt. locking
    public Long version;

    @ManyToOne
    public User participant;

    @ManyToOne
    public Talk talk;

    public int priorityValue; // 1 = Hoch, 2 = Mittel, 3 = Niedrig

    public LocalDateTime lastUpdated;
}