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
