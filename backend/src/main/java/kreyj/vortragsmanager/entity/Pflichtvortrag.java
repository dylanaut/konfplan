package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("PFLICHT")
public class Pflichtvortrag extends Vortrag {

    @ManyToOne(optional = false)
    public Raum pflichtraum;

    @ManyToOne(optional = false)
    public EventSlot pflichtslot;

    public Pflichtvortrag() {
    }

    @Override
    public boolean istPflicht() {
        return true;
    }
}
