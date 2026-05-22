package kreyj.konfplan.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
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
public class Gebaeude extends VersionedEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gebaeudetyp typ;

    @Column(nullable = false)
    private String strasse;

    private String hausnummer;

    @Column(nullable = false)
    private String postleitzahl;

    @Column(nullable = false)
    private String ort;

    @OneToMany(mappedBy = "gebaeude", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Raum> raeume = new HashSet<>();

    @ManyToMany(mappedBy = "gebaeude")
    Set<Veranstaltung> veranstaltungen = new HashSet<>();

    public Gebaeude(String name, String ort, String strasse, String plz, Gebaeudetyp gebaeudetyp) {
        super();
        this.name = name;
        this.ort = ort;
        this.strasse = strasse;
        this.postleitzahl = plz;
        this.typ = gebaeudetyp;
    }

    public Set<Raum> getRaeume() {
        return Collections.unmodifiableSet(raeume);
    }

    public void addRaum(Raum raum) {
        if (null == raum) {
            return;
        }

        if (raeume.add(raum)) {
            raum.setGebaeude(this);
        }
    }

    public void removeRaum(Raum raum) {
        if (null == raum) {
            return;
        }

        raeume.remove(raum);
        raum.setGebaeude(null);
    }

    public Set<Veranstaltung> getVeranstaltungen() {
        return Collections.unmodifiableSet(veranstaltungen);
    }
}