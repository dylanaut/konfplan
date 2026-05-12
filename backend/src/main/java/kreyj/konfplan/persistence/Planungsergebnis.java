package kreyj.konfplan.persistence;

import jakarta.persistence.*;

@Entity
public class Planungsergebnis extends VersionedEntity {

    @OneToOne
    @JoinColumn(name = "veranstaltung_id", nullable = false, unique = true)
    public Veranstaltung veranstaltung;

    @Lob
    @Column(nullable = false)
    public String jsonErgebnis;

    @Column(nullable = false)
    public String solver;

    @Column(nullable = false)
    public int timeout;
}
