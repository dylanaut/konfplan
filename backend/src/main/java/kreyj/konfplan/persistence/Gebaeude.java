package kreyj.konfplan.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
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

    @OneToMany(mappedBy = "gebaeude", cascade = CascadeType.ALL)
    private List<Raum> raeume = new ArrayList<>();

    @ManyToMany(mappedBy = "gebaeude")
    private List<Veranstaltung> veranstaltungen = new ArrayList<>();

    public Gebaeude() {
    }

    public Gebaeude(String name, String ort, String strasse, String plz, Gebaeudetyp gebaeudetyp) {
        super();
        this.name = name;
        this.ort = ort;
        this.strasse = strasse;
        this.postleitzahl = plz;
        this.typ = gebaeudetyp;
    }

    public void addVeranstaltung(Veranstaltung v) {
        if (this.veranstaltungen.contains(v)) {
            return;
        }
        this.veranstaltungen.add(v);
        v.addGebaeude(this);
    }

    public List<Raum> getRaeume() {
        return Collections.unmodifiableList(raeume);
    }

    public void addRaum(Raum raum) {
        if (this.raeume.contains(raum)) {
            return;
        }
        this.raeume.add(raum);
        raum.setGebaeude(this);
    }

    // -------------------------------------------------------------------
    // Helper classes and methods
    // -------------------------------------------------------------------

    public enum Gebaeudetyp {
        SCHULE, KINO, SPORTHALLE, SAAL, EXTERN
    }
}