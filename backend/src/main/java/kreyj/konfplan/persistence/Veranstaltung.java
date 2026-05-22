package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotEmpty;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "beginntAm"})
})
public class Veranstaltung extends VersionedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime beginntAm;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime endetAm;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime deadlineReferenten;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime deadlineTeilnehmer;

    private String logo;

    private String logo_link;

    @ManyToMany
    @JoinTable(
            name = "Veranstaltung_Gebaeude",
            joinColumns = @JoinColumn(name = "veranstaltung_id"),
            inverseJoinColumns = @JoinColumn(name = "gebaeude_id")
    )
    private Set<Gebaeude> gebaeude = new HashSet<>();

    public Set<Gebaeude> getGebaeude() {
        return Collections.unmodifiableSet(gebaeude);
    }


    public void addGebaeude(Gebaeude g) {
        if (null == g) {
            return;
        }

        if (gebaeude.add(g)) {
            g.veranstaltungen.add(this);
        }
    }

    public void removeGebaeude(Gebaeude g) {
        if (null == g) {
            return;
        }

        if (gebaeude.remove(g)) {
            g.veranstaltungen.remove(this);
        }
    }

    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    Set<EventSlot> eventSlots = new HashSet<>();

    public Set<EventSlot> getEventSlots() {
        return Collections.unmodifiableSet(eventSlots);
    }


    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL, orphanRemoval = true)
     Set<Vortrag> vortraege = new HashSet<>();

    public Set<Vortrag> getVortraege() {
        return Collections.unmodifiableSet(vortraege);
    }


    @ManyToMany(mappedBy = "veranstaltungen")
    @JsonIgnoreProperties("veranstaltungen")
    Set<Nutzer> nutzer = new HashSet<>();

    public Set<Nutzer> getNutzer() {
        return Collections.unmodifiableSet(nutzer);
    }

    // TODO detect and remove
    public void setNutzer(Set<Nutzer> newNutzer) {
        throw new UnsupportedOperationException();
    }


    @NotEmpty(message = "Veranstaltung muss mindestens eine/n Organisator/in haben")
    public Set<Admin> organisatoren() {
        return nutzer.stream().filter(u -> u instanceof Admin)
                .map(u -> (Admin) u)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<Teilnehmer> teilnehmer() {
        return nutzer.stream().filter(u -> u instanceof Teilnehmer)
                .map(u -> (Teilnehmer) u)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<Referent> referenten() {
        return nutzer.stream().filter(u -> u instanceof Referent)
                .map(u -> (Referent) u)
                .collect(Collectors.toUnmodifiableSet());
    }
}