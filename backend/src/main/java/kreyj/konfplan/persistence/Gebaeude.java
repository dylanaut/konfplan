package kreyj.konfplan.persistence;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Gebaeude extends VersionedEntity {

    @Column(nullable = false, unique = true)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Gebaeudetyp typ;

    @Column(nullable = false)
    public String strasse;

    public String hausnummer;

    @Column(nullable = false)
    public String postleitzahl;

    @Column(nullable = false)
    public String ort;

    @OneToMany(mappedBy = "gebaeude", cascade = CascadeType.ALL)
    public List<Raum> raeume = new ArrayList<>();

    @ManyToMany(mappedBy = "gebaeude")
    public List<Veranstaltung> veranstaltungen = new ArrayList<>();

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

    // -------------------------------------------------------------------
    // Helper classes and methods
    // -------------------------------------------------------------------

    public enum Gebaeudetyp {
        SCHULE, KINO, SPORTHALLE, SAAL, EXTERN
    }
}


