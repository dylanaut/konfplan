package kreyj.konfplan.persistence;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("PFLICHT")
public class Pflichtvortrag extends Vortrag {
    public String pflichtgruppe;

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
