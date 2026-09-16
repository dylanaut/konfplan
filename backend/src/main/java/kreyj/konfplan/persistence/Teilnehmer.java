package kreyj.konfplan.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@DiscriminatorValue("TEILNEHMER")
public class Teilnehmer extends Nutzer {

    /**
     * @deprecated Wird durch {@link #gruppenwerte} (strukturierte Gruppenkategorien, siehe #690)
     * abgelöst. Bleibt vorerst additiv bestehen, bis alle Konsumenten (Pflichtvortrag-Matching,
     * Reports, Frontend) umgestellt sind.
     */
    @Deprecated
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "teilnehmer_gruppen", joinColumns = @JoinColumn(name = "teilnehmer_id"))
    @Column(name = "gruppen")
    private Set<String> gruppen = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "teilnehmer_gruppenwert",
        joinColumns = @JoinColumn(name = "teilnehmer_id"),
        inverseJoinColumns = @JoinColumn(name = "gruppenkategoriewert_id")
    )
    private Set<GruppenkategorieWert> gruppenwerte = new HashSet<>();

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Prioritaet> prioritaeten = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "teilnehmer_neigungen", joinColumns = @JoinColumn(name = "teilnehmer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "neigung", length = 50)
    private Set<Neigung> neigungen = new HashSet<>();


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


    public void setGruppen(Collection<String> neueGruppen) {
        gruppen.clear();
        if (null != neueGruppen) {
            neueGruppen.forEach(this::addGruppe);
        }
    }


    public Set<GruppenkategorieWert> getGruppenwerte() {
        return Collections.unmodifiableSet(gruppenwerte);
    }


    public boolean hatGruppenwert(GruppenkategorieWert wert) {
        return null != wert && gruppenwerte.contains(wert);
    }


    /**
     * Ordnet dem Teilnehmer einen Gruppenwert zu. Ist die Kategorie nicht
     * {@link Gruppenkategorie#isMehrwertig() mehrwertig}, ersetzt der neue Wert einen ggf. bereits
     * zugeordneten Wert derselben Kategorie (max. ein Wert pro einwertiger Kategorie).
     */
    public void addGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        if (!wert.getGruppenkategorie().isMehrwertig()) {
            gruppenwerte.removeIf(vorhandener -> {
                boolean gleicheKategorie = vorhandener.getGruppenkategorie().getId().equals(wert.getGruppenkategorie().getId());
                if (gleicheKategorie) {
                    vorhandener.entferneTeilnehmer(this);
                }
                return gleicheKategorie;
            });
        }
        gruppenwerte.add(wert);
        wert.nimmTeilnehmerAuf(this);
    }


    public void removeGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        if (gruppenwerte.remove(wert)) {
            wert.entferneTeilnehmer(this);
        }
    }


    /**
     * Prüft die Gruppenmitgliedschaft gegen BEIDE Modelle: das bisherige flache {@link #gruppen}
     * und die neuen strukturierten {@link #gruppenwerte} (siehe #690) - damit Pflichtvortrag-
     * Zuordnungen unabhängig davon greifen, über welches der beiden Systeme einem Teilnehmer
     * diese Gruppe zugewiesen wurde. {@code gruppenwerte} wird dabei nur innerhalb derselben
     * Veranstaltung berücksichtigt (ein Wert gehört zu genau einer Gruppenkategorie einer
     * Veranstaltung).
     */
    public boolean istInGruppe(String gruppenName, Veranstaltung veranstaltung) {
        if (null == gruppenName) {
            return false;
        }
        if (gruppen.contains(gruppenName)) {
            return true;
        }
        return gruppenwerte.stream().anyMatch(wert -> wert.getWert().equals(gruppenName)
            && wert.getGruppenkategorie().getVeranstaltung().getId().equals(veranstaltung.getId()));
    }


    public static List<Teilnehmer> getGruppenTeilnehmer(String gruppenName, Veranstaltung veranstaltung) {
        return Teilnehmer.find("SELECT DISTINCT tn from Teilnehmer tn " +
                " JOIN tn.veranstaltungen v " +
                " WHERE v = ?2 AND tn.isActive = true " +
                " AND (?1 MEMBER OF tn.gruppen " +
                "      OR EXISTS (SELECT gkw FROM GruppenkategorieWert gkw " +
                "                 WHERE gkw MEMBER OF tn.gruppenwerte " +
                "                 AND gkw.wert = ?1 AND gkw.gruppenkategorie.veranstaltung = v))",
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


    public Set<Neigung> getNeigungen() {
        return Collections.unmodifiableSet(neigungen);
    }


    public void setNeigungen(Set<Neigung> neueNeigungen) {
        neigungen.clear();
        if (null != neueNeigungen) {
            neigungen.addAll(neueNeigungen);
        }
    }
}
