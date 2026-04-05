package kreyj.vortragsmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "beginntAm"})
})
public class Veranstaltung extends SqliteEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public LocalDateTime beginntAm;

    public LocalDateTime endetAm;

    // Das Attribut 'ort' wird entfernt, da es durch die Relation zu Gebaeude ersetzt wird.
    // public String ort;

    public String logo;

    public String logo_link;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organisator_id", columnDefinition = "INTEGER")
    public User organisator;

    @ManyToMany
    @JoinTable(
        name = "Veranstaltung_Gebaeude",
        joinColumns = @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER"),
        inverseJoinColumns = @JoinColumn(name = "gebaeude_id", columnDefinition = "INTEGER")
    )
    public List<Gebaeude> gebaeude = new ArrayList<>();

    @Version
    public Long version;
}
