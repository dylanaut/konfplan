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
    private Vortrag vortrag;

    private int prioWert;

    private LocalDateTime lastUpdated;


    public Prioritaet(Teilnehmer teilnehmer, Wahlvortrag wahlvortrag, int prio) {
        this.teilnehmer = teilnehmer;
        this.vortrag = wahlvortrag;
        this.prioWert = prio;
        this.lastUpdated = LocalDateTime.now();
    }
}