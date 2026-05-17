package kreyj.konfplan.persistence;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("WAHL")
public class Wahlvortrag extends Vortrag {

    public boolean wiederholbar;

    public int maxWiederholungen = 1;

    @Override
    public boolean istPflicht() {
        return false;
    }

}
