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
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends Nutzer {

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "teilnehmer_gruppen", joinColumns = @JoinColumn(name = "teilnehmer_id"))
    @Column(name = "gruppen")
    private Set<String> gruppen = new HashSet<>();

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL, orphanRemoval = true)
    Set<Prioritaet> prioritaeten = new HashSet<>();


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------


    public Teilnehmer() {
        this.setRole("TEILNEHMER");
    }


    // -------------------------------------------------------------------
    // public methods
    // -------------------------------------------------------------------


    public Set<String> getGruppen() {
        return Collections.unmodifiableSet(gruppen);
    }


    public boolean gehoertZuGruppe(String gruppe) {
        if (null == gruppe) {
            return false;
        }
        return gruppen.contains(gruppe);
    }


    public void addGruppe(String gruppe) {
        if (StringUtils.isBlank(gruppe)) {
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


    public static List<Teilnehmer> getGruppenTeilnehmer(String gruppenName, Veranstaltung veranstaltung) {
        return Teilnehmer.find("SELECT tn from Teilnehmer tn " +
                " JOIN tn.veranstaltungen v " +
                " WHERE ?1 MEMBER OF tn.gruppen " +
                " AND v = ?2 and tn.isActive = true",
            gruppenName, veranstaltung).list();
    }


    public Set<Prioritaet> getPrioritaeten() {
        return Collections.unmodifiableSet(prioritaeten);
    }




    public void addPrioritaet(Prioritaet prioritaet) {
        if (null == prioritaet) {
            return;
        }
        prioritaeten.add(prioritaet);
    }


    public void removePrioritaet(Prioritaet prioritaet) {
        if (null == prioritaet) {
            return;
        }
        prioritaeten.remove(prioritaet);
    }
}
