package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Prioritaet extends SqliteEntity {
    @Version
    public Long version;

    @ManyToOne
    public Teilnehmer teilnehmer;

    @ManyToOne
    public Vortrag vortrag;

    public int prioWert; // 1 = Hoch, 2 = Mittel, 3 = Niedrig

    public LocalDateTime lastUpdated;

    public Prioritaet() {}
}
