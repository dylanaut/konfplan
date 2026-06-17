package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Prioritaet extends IdEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    private Teilnehmer teilnehmer;

    @ManyToOne(fetch = FetchType.LAZY)
    private Wahlvortrag vortrag;

    private int prioWert;


    public Prioritaet(Teilnehmer teilnehmer, Wahlvortrag wahlvortrag, int prio) {
        this.teilnehmer = teilnehmer;
        this.vortrag = wahlvortrag;
        this.prioWert = prio;
    }
}