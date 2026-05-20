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
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
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
    private List<Gebaeude> gebaeude = new ArrayList<>();

    public List<Gebaeude> getGebaeude() {
        return Collections.unmodifiableList(gebaeude);
    }


    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL)
    private Set<EventSlot> eventSlots = new HashSet<>();

    public Set<EventSlot> getEventSlots() {
        return Collections.unmodifiableSet(eventSlots);
    }

    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL)
    private Set<Vortrag> vortraege = new HashSet<>();


    public Set<Vortrag> getVortraege() {
        return Collections.unmodifiableSet(vortraege);
    }

    @ManyToMany(mappedBy = "veranstaltungen")
    @JsonIgnoreProperties("veranstaltungen")
    private Set<Nutzer> nutzer = new HashSet<>();

    public Set<Nutzer> getNutzer() {
        return Collections.unmodifiableSet(nutzer);
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

    public void addSlot(EventSlot slot) {
        if (this.eventSlots.contains(slot)) {
            return;
        }
        this.eventSlots.add(slot);
        slot.setVeranstaltung(this);
    }

    public void removeSlot(EventSlot slot) {
        if (this.eventSlots.contains(slot)) {
            this.eventSlots.remove(slot);
            slot.setVeranstaltung(null);
        }
    }

    public void addVortrag(Vortrag vortrag) {
        if (this.vortraege.contains(vortrag)) {
            return;
        }
        this.vortraege.add(vortrag);
        vortrag.setVeranstaltung(this);
    }

    public void removeVortrag(Vortrag vortrag) {
        if (this.vortraege.contains(vortrag)) {
            this.vortraege.remove(vortrag);
            vortrag.setVeranstaltung(null);
        }
    }

    public void addNutzer(Nutzer nutzer) {
        if (this.nutzer.contains(nutzer)) {
            return;
        }
        this.nutzer.add(nutzer);
        nutzer.addVeranstaltung(this);
    }

    public void removeNutzer(Nutzer nutzer) {
        this.nutzer.remove(nutzer);

        if (nutzer.getVeranstaltungen().contains(this)) {
            nutzer.removeVeranstaltung(this);
        }
    }

    public void addGebaeude(Gebaeude gebaeude) {
        if (this.gebaeude.contains(gebaeude)) {
            return;
        }
        this.gebaeude.add(gebaeude);
        gebaeude.getVeranstaltungen().add(this);
    }

    public void clearGebaeude() {
        for (Gebaeude g : this.gebaeude) {
            g.getVeranstaltungen().remove(this);
        }
        this.gebaeude.clear();
    }
}