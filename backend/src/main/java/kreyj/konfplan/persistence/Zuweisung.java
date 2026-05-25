package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Zuweisung extends VersionedEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "teilnehmer_id")
    private Teilnehmer teilnehmer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "vortrag_id")
    private Vortrag vortrag;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "eventslot_id")
    private Slot slot;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "raum_id")
    private Raum raum;
}