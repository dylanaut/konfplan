package kreyj.konfplan.persistence;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Planungsergebnis extends VersionedEntity {

    @OneToOne
    @JoinColumn(name = "veranstaltung_id", nullable = false, unique = true)
    private Veranstaltung veranstaltung;

    @Lob
    @Column(nullable = false)
    @Basic(fetch = FetchType.EAGER) // Ensure eager loading of the LOB
    private String jsonErgebnis;

    @Column(nullable = false)
    private String solver;

    @Column(nullable = false)
    private int timeout;
}