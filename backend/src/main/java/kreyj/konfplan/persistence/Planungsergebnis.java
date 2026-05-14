package kreyj.konfplan.persistence;

import jakarta.persistence.*;
import kreyj.konfplan.service.OptimierungService;

@Entity
public class Planungsergebnis extends VersionedEntity {

    @OneToOne
    @JoinColumn(name = "veranstaltung_id", nullable = false, unique = true)
    public Veranstaltung veranstaltung;

    @Lob
    @Column(nullable = false)
    @Basic(fetch = FetchType.EAGER) // Ensure eager loading of the LOB
    public String jsonErgebnis;

    @Column(nullable = false)
    public OptimierungService.SOLVER_TYP solver;

    @Column(nullable = false)
    public int timeout;
}