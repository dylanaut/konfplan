package kreyj.konfplan.persistence;

import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@DiscriminatorValue("REFERENT")
public class Referent extends Nutzer {

    @Column(name = "job_role")
    @CsvBindByName(column = "Position")
    private String jobRole;

    @Column(name = "organisation")
    @CsvBindByName(column = "Organisation")
    private String organisation;

    @OneToMany(mappedBy = "referent", cascade = CascadeType.ALL, orphanRemoval = true)
    Set<Vortrag> vortraege = new HashSet<>();

    public Set<Vortrag> getVortraege() {
        return Collections.unmodifiableSet(vortraege);
    }

    public void addVortrag(Vortrag aVortrag) {
        if (null == aVortrag) {
            return;
        }

        vortraege.add(aVortrag);
        aVortrag.referent = this;
    }

    public void removeVortrag(Vortrag aVortrag) {
        if (null == aVortrag) {
            return;
        }

        vortraege.remove(aVortrag);
        aVortrag.referent = null;
    }

    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Referent() {
        this.setRole("REFERENT");
    }
}