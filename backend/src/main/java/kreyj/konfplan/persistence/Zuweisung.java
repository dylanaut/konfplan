package kreyj.konfplan.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Zuweisung extends VersionedEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "teilnehmer_id")
    private Teilnehmer teilnehmer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vortrag_id")
    private Vortrag vortrag;

    @ManyToOne(optional = false)
    @JoinColumn(name = "eventslot_id")
    private EventSlot slot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "raum_id")
    private Raum raum;

    public Zuweisung() {
    }
}