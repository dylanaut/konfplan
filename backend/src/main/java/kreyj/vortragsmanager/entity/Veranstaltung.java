package kreyj.vortragsmanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import kreyj.vortragsmanager.entity.converter.LocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime deadlineReferenten;

    @Convert(converter = LocalDateTimeConverter.class)
    public LocalDateTime deadlineTeilnehmer;

    public String logo;

    public String logo_link;

    @ManyToMany
    @JoinTable(
            name = "Veranstaltung_Gebaeude",
            joinColumns = @JoinColumn(name = "veranstaltung_id"),
            inverseJoinColumns = @JoinColumn(name = "gebaeude_id")
    )
    public List<Gebaeude> gebaeude = new ArrayList<>();

    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL)
    public Set<EventSlot> eventSlots = new HashSet<>();

    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL)
    public Set<Vortrag> vortraege = new HashSet<>();

    @ManyToMany(mappedBy = "veranstaltungen")
    @JsonIgnoreProperties("veranstaltungen")
    public Set<Nutzer> nutzer = new HashSet<>();

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

    public void addSlot(EventSlot slot) {
        if (this.eventSlots.contains(slot)) {
            return;
        }
        this.eventSlots.add(slot);
        slot.veranstaltung = this;
    }

    public void addVortrag(Vortrag vortrag) {
        if (this.vortraege.contains(vortrag)) {
            return;
        }
        this.vortraege.add(vortrag);
        vortrag.veranstaltung = this;
    }

    public void addNutzer(Nutzer nutzer) {
        if (this.nutzer.contains(nutzer)) {
            return;
        }
        this.nutzer.add(nutzer);
        nutzer.addVeranstaltung(this);
    }

    public void addGebaeude(Gebaeude gebaeude) {
        if (this.gebaeude.contains(gebaeude)) {
            return;
        }
        this.gebaeude.add(gebaeude);
        gebaeude.veranstaltungen.add(this);
    }
}
