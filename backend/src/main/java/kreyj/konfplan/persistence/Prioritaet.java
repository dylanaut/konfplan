package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Prioritaet extends IdEntity {
    public static final int PRIO_MIN = 0;
    public static final int PRIO_MAX = 10;

    @ManyToOne(fetch = FetchType.LAZY)
    private Teilnehmer teilnehmer;

    @ManyToOne(fetch = FetchType.LAZY)
    private Wahlvortrag vortrag;

    @Min(PRIO_MIN)
    @Max(PRIO_MAX)
    private int prioWert;


    public Prioritaet(Teilnehmer teilnehmer, Wahlvortrag wahlvortrag, int prioWert) {
        this.teilnehmer = teilnehmer;
        this.vortrag = wahlvortrag;
        this.prioWert = prioWert;
    }
}
