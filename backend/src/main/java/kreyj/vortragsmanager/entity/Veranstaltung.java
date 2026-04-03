package kreyj.vortragsmanager.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
public class Veranstaltung extends SqliteEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public LocalDateTime beginntAm;

    public LocalDateTime endetAm;

    @Column(nullable = false)
    public String ort;

    public String logo;

    public String logo_link;

    @ManyToOne(optional = false)
    public User organisator;

    @Version
    public Long version;
}
