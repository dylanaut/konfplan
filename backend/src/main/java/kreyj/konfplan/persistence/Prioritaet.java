package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Prioritaet extends IdEntity {
    @ManyToOne
    private Teilnehmer teilnehmer;

    @ManyToOne
    private Vortrag vortrag;

    private int prioWert;

    private LocalDateTime lastUpdated;

    public Prioritaet() {
    }

    public Prioritaet(Teilnehmer teilnehmer, Wahlvortrag wahlvortrag, int prio) {
        this.teilnehmer = teilnehmer;
        this.vortrag = wahlvortrag;
        this.prioWert = prio;
        this.lastUpdated = LocalDateTime.now();
    }
}