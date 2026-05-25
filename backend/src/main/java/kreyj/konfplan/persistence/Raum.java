package kreyj.konfplan.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Raum extends VersionedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer kapazitaet;

    private String etage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gebaeude_id")
    private Gebaeude gebaeude;

    void setGebaeude(Gebaeude gebaeude) {
        this.gebaeude = gebaeude;
    }


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Raum(String name, int kapazitaet) {
        super();
        this.name = name;
        this.kapazitaet = kapazitaet;
    }
}