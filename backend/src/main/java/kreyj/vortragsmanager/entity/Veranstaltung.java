package kreyj.vortragsmanager.entity;

import jakarta.persistence.*;
import kreyj.vortragsmanager.entity.converter.LocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "beginntAm"})
})
public class Veranstaltung extends VersionedEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime beginntAm;

    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime endetAm;

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

    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL)
    public Set<EventSlot> eventSlots = new HashSet<>();
}
