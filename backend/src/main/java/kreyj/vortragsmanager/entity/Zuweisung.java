package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
public class Zuweisung extends SqliteEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "teilnehmer_id", columnDefinition = "INTEGER")
    public Teilnehmer teilnehmer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vortrag_id", columnDefinition = "INTEGER")
    public Vortrag vortrag;

    @ManyToOne(optional = false)
    @JoinColumn(name = "eventslot_id", columnDefinition = "INTEGER")
    public EventSlot slot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "raum_id", columnDefinition = "INTEGER")
    public Raum raum;

    public Zuweisung() {}
}
