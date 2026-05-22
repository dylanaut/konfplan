package kreyj.konfplan.persistence;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
@DiscriminatorValue("WAHL")
public class Wahlvortrag extends Vortrag {

    private boolean wiederholbar;

    private int maxWiederholungen = 1;

    @Override
    public boolean istPflicht() {
        return false;
    }

}