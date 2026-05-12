package kreyj.konfplan.persistence;

import jakarta.persistence.*;

@Entity
public class Zuweisung extends VersionedEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "teilnehmer_id")
    public Teilnehmer teilnehmer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vortrag_id")
    public Vortrag vortrag;

    @ManyToOne(optional = false)
    @JoinColumn(name = "eventslot_id")
    public EventSlot slot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "raum_id")
    public Raum raum;

    public Zuweisung() {}
}
