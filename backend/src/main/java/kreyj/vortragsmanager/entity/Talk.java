package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

@Entity
public class Talk extends PanacheEntity {
    @Version  // opt. locking
    public Long version;

    public String title;

    @Column(columnDefinition = "TEXT")
    public String abstractText;
    public String targetAudience;
    public int maxRepetitions = 1;
    @ManyToOne
    public User speaker;

    // In Talk.java ergänzen:
    public boolean readyToRepeat; // Grundsätzliche Bereitschaft
    public int maxPossibleRepetitions; // Wie oft maximal?
}