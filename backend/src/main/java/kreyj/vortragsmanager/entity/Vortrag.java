package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
public class Vortrag extends SqliteEntity {
    @Version  // opt. locking
    public Long version;

    public String title;

    @Column(columnDefinition = "TEXT")
    public String abstractText;
    public String targetAudience;
    public int maxRepetitions = 1;
    @ManyToOne
    public Referent referent;

    public boolean readyToRepeat; // Grundsätzliche Bereitschaft
    
    public boolean istPflicht = false; // Neu: Pflichtvortrag-Flag
}
