package kreyj.konfplan.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Definiert für eine Veranstaltung eine Kategorie von Teilnehmer-Gruppenwerten (z.B. "Schule",
 * "Messe", "Klasse") - ersetzt das bisherige flache, unstrukturierte {@code Teilnehmer.gruppen}
 * (siehe #690). {@link #werte} bildet den erlaubten Wertebereich dieser Kategorie ab;
 * {@link #mehrwertig} legt fest, ob ein Teilnehmer mehrere Werte dieser Kategorie gleichzeitig
 * besitzen darf, {@link #pflicht}, ob jeder Teilnehmer mindestens einen Wert besitzen muss.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "UK_GRUPPENKATEGORIE_VERANSTALTUNG_NAME", columnNames = {"veranstaltung_id", "name"}))
@NoArgsConstructor
@Getter
@Setter
public class Gruppenkategorie extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veranstaltung_id")
    private Veranstaltung veranstaltung;

    private String name;

    private boolean mehrwertig;

    private boolean pflicht;

    @OneToMany(mappedBy = "gruppenkategorie", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<GruppenkategorieWert> werte = new HashSet<>();


    public Gruppenkategorie(Veranstaltung veranstaltung, String name, boolean mehrwertig, boolean pflicht) {
        this.veranstaltung = veranstaltung;
        this.name = name;
        this.mehrwertig = mehrwertig;
        this.pflicht = pflicht;
    }


    public Set<GruppenkategorieWert> getWerte() {
        return Collections.unmodifiableSet(werte);
    }


    /**
     * Hält die inverse Seite der {@link #werte}-Assoziation synchron - von
     * {@link GruppenkategorieWert}s Konstruktor aufgerufen. Ohne diesen expliziten Java-seitigen
     * Sync bleibt die Kollektion, sobald sie einmal (lazy) initialisiert ist, gegenüber neu
     * angelegten Werten inkonsistent, bis sie neu geladen wird - u.a. verwechselt Hibernates
     * Cascade-Verarbeitung beim Löschen der Kategorie einen so übersehenen Wert sonst mit einer
     * verwaisten, transienten Referenz (TransientPropertyValueException).
     */
    void nimmWertAuf(GruppenkategorieWert wert) {
        werte.add(wert);
    }


    /**
     * Hält die inverse Seite der {@link #werte}-Assoziation synchron, wenn ein einzelner Wert
     * gelöscht wird, während die Kategorie (und damit diese Kollektion) bereits geladen ist - ohne
     * diesen Sync würde Hibernates {@code CascadeType.ALL} den gerade per {@code wert.delete()}
     * gelöschten Wert beim nächsten Flush über die weiterhin vorhandene Collection-Mitgliedschaft
     * erneut persistieren (siehe #719). {@code removeIf} statt {@code Set.remove}, weil
     * {@link IdEntity#hashCode()} sich ändert, sobald der bei {@link #nimmWertAuf} noch
     * unpersistierte (ID {@code null}) Wert seine generierte ID erhält - eine hashcode-basierte
     * {@code HashSet}-Suche würde ihn dann im falschen Bucket suchen und ihn fälschlich als nicht
     * vorhanden ansehen; {@code removeIf} durchläuft dagegen alle Buckets linear.
     */
    public void entferneWert(GruppenkategorieWert wert) {
        werte.removeIf(wert::equals);
    }
}
