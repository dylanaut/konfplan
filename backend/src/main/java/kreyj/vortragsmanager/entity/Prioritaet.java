package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Prioritaet extends IdEntity {
    @ManyToOne
    public Teilnehmer teilnehmer;

    @ManyToOne
    public Vortrag vortrag;

    public int prioWert;

    public LocalDateTime lastUpdated;

    public Prioritaet() {}
}
