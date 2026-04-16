package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Prioritaet extends VersionedEntity {
    @ManyToOne
    public Teilnehmer teilnehmer;

    @ManyToOne
    public Vortrag vortrag;

    public int prioWert; // 1 = Hoch, 2 = Mittel, 3 = Niedrig

    public LocalDateTime lastUpdated;

    public Prioritaet() {}
}
