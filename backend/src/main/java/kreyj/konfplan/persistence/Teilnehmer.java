package kreyj.konfplan.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends Nutzer {

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "teilnehmer_gruppen", joinColumns = @JoinColumn(name = "teilnehmer_id"))
    @Column(name = "gruppen")
    private Set<String> gruppen = new HashSet<>();

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Prioritaet> prioritaeten = new ArrayList<>();


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Teilnehmer() {
        this.setRole("TEILNEHMER");
    }


    // -------------------------------------------------------------------
    // public methods
    // -------------------------------------------------------------------

    public boolean hasGruppe(String gruppe) {
        if (null == gruppe) {
            return false;
        }
        return gruppen.contains(gruppe);
    }

    public void addGruppe(String gruppe) {
        if (null == gruppe || gruppe.isBlank()) {
            return;
        }
        gruppen.add(gruppe);
    }

    public void removeGruppe(String gruppe) {
        if (null == gruppe) {
            return;
        }
        gruppen.remove(gruppe);
    }

    public static List<Teilnehmer> getGruppenTeilnehmer(String gruppenName, Long veranstaltungId) {
        return Teilnehmer.find("SELECT tn FROM Teilnehmer tn JOIN tn.veranstaltungen v " +
                        " WHERE ?1 MEMBER OF tn.gruppen " +
                        " AND v.id = ?2 and tn.isActive = true",
                gruppenName, veranstaltungId).list();

    }

    public static List<Teilnehmer> getVeranstaltungTeilnehmer(Long veranstaltungId) {
        return Teilnehmer.find("SELECT tn FROM Teilnehmer tn JOIN tn.veranstaltungen v WHERE v.id = ?1 and tn.isActive = true", veranstaltungId).list();
    }
}