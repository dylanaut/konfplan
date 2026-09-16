package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Ein einzelner erlaubter Wert innerhalb des Wertebereichs einer {@link Gruppenkategorie} (z.B.
 * "RKS" in der Kategorie "Schule", "M_1" in der Kategorie "Messe"). Teilnehmer referenzieren
 * diese Entity direkt (statt eines freien Strings) - damit bleibt ein Umbenennen eines Wertes
 * (siehe bisher {@code OrganisatorService#renameGruppe}) eine reine Attributänderung, ohne alle
 * Teilnehmer-Zuordnungen umschreiben zu müssen.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "UK_GRUPPENKATEGORIEWERT_KATEGORIE_WERT", columnNames = {"gruppenkategorie_id", "wert"}))
@NoArgsConstructor
@Getter
@Setter
public class GruppenkategorieWert extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gruppenkategorie_id")
    private Gruppenkategorie gruppenkategorie;

    private String wert;

    @JsonIgnore
    @ManyToMany(mappedBy = "gruppenwerte")
    @Setter(AccessLevel.NONE)
    private Set<Teilnehmer> teilnehmer = new HashSet<>();


    public GruppenkategorieWert(Gruppenkategorie gruppenkategorie, String wert) {
        this.gruppenkategorie = gruppenkategorie;
        this.wert = wert;
        gruppenkategorie.nimmWertAuf(this);
    }


    public Set<Teilnehmer> getTeilnehmer() {
        return Collections.unmodifiableSet(teilnehmer);
    }


    /**
     * Sucht über alle Gruppenkategorien einer Veranstaltung hinweg nach einem Wert mit exakt
     * diesem Namen (siehe #690, u.a. für den CSV-Import: ein importierter Gruppen-/Pflichtgruppen-
     * Name kann zusätzlich zum bisherigen flachen Modell auch einer strukturierten Kategorie
     * entsprechen).
     */
    public static GruppenkategorieWert findByWertUndVeranstaltung(String wert, Veranstaltung veranstaltung) {
        return find("wert = ?1 and gruppenkategorie.veranstaltung = ?2", wert, veranstaltung).firstResult();
    }


    /**
     * Sucht innerhalb einer einzelnen Gruppenkategorie nach einem Wert mit exakt diesem Namen -
     * anders als {@link #findByWertUndVeranstaltung} eindeutig auch dann, wenn zwei Kategorien
     * derselben Veranstaltung zufällig gleich benannte Werte besitzen (siehe #690, CSV-Import
     * über explizite Gruppenkategorie-Spalten statt eines einzigen flachen Gruppen-Feldes).
     */
    public static GruppenkategorieWert findByWertUndKategorie(String wert, Gruppenkategorie kategorie) {
        return find("wert = ?1 and gruppenkategorie = ?2", wert, kategorie).firstResult();
    }


    /**
     * Hält die inverse Seite der {@code Teilnehmer.gruppenwerte}-Assoziation synchron - von
     * {@link Teilnehmer#addGruppenwert} aufgerufen (siehe {@link Gruppenkategorie#nimmWertAuf}
     * für dieselbe Notwendigkeit bei der anderen Assoziation dieser Klasse).
     */
    void nimmTeilnehmerAuf(Teilnehmer teilnehmer) {
        this.teilnehmer.add(teilnehmer);
    }


    void entferneTeilnehmer(Teilnehmer teilnehmer) {
        this.teilnehmer.remove(teilnehmer);
    }
}
