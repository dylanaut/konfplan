package kreyj.konfplan.persistence;

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

    public Prioritaet(Teilnehmer teilnehmer, Wahlvortrag wahlvortrag, int prio) {
        this.teilnehmer = teilnehmer;
        this.vortrag = wahlvortrag;
        this.prioWert = prio;
        this.lastUpdated = LocalDateTime.now();
    }
}
