package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
import java.util.List;
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "veranstaltung_gruppen", joinColumns = @JoinColumn(name = "veranstaltung_id"))
    @Column(name = "gruppen")
    private Set<String> gruppen = new HashSet<>();

    public Set<String> getGruppen() {
        return Collections.unmodifiableSet(gruppen);
    }

    public boolean addGruppe(String gruppenName) {
        if (null == gruppenName) {
            return false;
        }

        return gruppen.add(gruppenName);
    }

    public boolean removeGruppe(String gruppenName) {
        if (null == gruppenName) {
            return false;
        }

        return gruppen.remove(gruppenName);
    }


    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "Veranstaltung_Gebaeude",
            joinColumns = @JoinColumn(name = "veranstaltung_id"),
            inverseJoinColumns = @JoinColumn(name = "gebaeude_id")
    )
    private Set<Gebaeude> gebaeude = new HashSet<>();

    public Set<Gebaeude> getGebaeude() {
        return Collections.unmodifiableSet(gebaeude);
    }

    public void addGebaeude(Gebaeude aGebaeude) {
        if (null == aGebaeude) {
            return;
        }

        this.gebaeude.add(aGebaeude);
        aGebaeude.veranstaltungen.add(this);

        for (Raum raum : aGebaeude.raeume) {
            for (Slot slot : slots) {
                raum.updateRaumVerfuegbarkeit(slot, this, true, true);
            }
        }
    }

    public void removeGebaeude(Gebaeude aGebaeude) {
        if (null == aGebaeude) {
            return;
        }
        if (!gebaeude.contains(aGebaeude)) {
            return;
        }

        aGebaeude.raeume.forEach(raum -> raum.deleteRaumVerfuegbarkeit(this));

        gebaeude.remove(aGebaeude);
        aGebaeude.veranstaltungen.remove(this);
    }


    @OneToMany(mappedBy = "veranstaltung", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    Set<Slot> slots = new HashSet<>();

    public Set<Slot> getSlots() {
        return Collections.unmodifiableSet(slots);
    }

    @JsonIgnore
    public Set<Long> getSlotIds() {
        return slots.stream().map(Slot::getId).collect(Collectors.toSet());
    }

    public void addSlot(Slot slot) {
        if (null == slot) {
            return;
        }

        slots.add(slot);
        slot.veranstaltung = this;

        nutzer.forEach(n -> n.updateVerfuegbarkeit(slot, this, true, true));
        gebaeude.stream().flatMap(g -> g.getRaeume().stream())
                .forEach(r -> r.updateRaumVerfuegbarkeit(slot, this, true, true));
        vortraege.stream()
                .filter(v -> !v.istPflicht())
                .forEach(v -> v.updateVerfuegbarkeit(slot, this, true, true));
    }

    public void removeSlot(Slot slot) {
        if (null == slot) {
            return;
        }

        slots.remove(slot);
        slot.veranstaltung = null;
        nutzer.forEach(n -> n.updateVerfuegbarkeit(slot, this, false, false));
        gebaeude.stream().flatMap(g -> g.getRaeume().stream())
                .forEach(r -> r.updateRaumVerfuegbarkeit(slot, this, false, false));
        vortraege.stream()
                .filter(v -> !v.istPflicht())
                .forEach(v -> v.updateVerfuegbarkeit(slot, this, false, false));
    }


    @OneToMany(mappedBy = "veranstaltung", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    Set<Vortrag> vortraege = new HashSet<>();

    public Set<Vortrag> getVortraege() {
        return Collections.unmodifiableSet(vortraege);
    }

    public void addVortrag(Vortrag aVortrag) {
        if (null == aVortrag) {
            return;
        }

        vortraege.add(aVortrag);
        aVortrag.veranstaltung = this;
    }

    public void removeVortrag(Vortrag aVortrag) {
        if (null == aVortrag) {
            return;
        }

        vortraege.remove(aVortrag);
        aVortrag.veranstaltung = null;
    }

    public List<Wahlvortrag> getWahlvortraege() {
        return vortraege.stream().filter(v -> v instanceof Wahlvortrag)
                .map(v -> (Wahlvortrag) v)
                .toList();
    }


    public List<Pflichtvortrag> getPflichtvortraege() {
        return vortraege.stream().filter(v -> v instanceof Pflichtvortrag)
                .map(v -> (Pflichtvortrag) v)
                .toList();
    }


    @ManyToMany(mappedBy = "veranstaltungen")
    @JsonIgnoreProperties("veranstaltungen")
    Set<Nutzer> nutzer = new HashSet<>();

    public Set<Nutzer> getNutzer() {
        return Collections.unmodifiableSet(nutzer);
    }

    public void addNutzer(Nutzer nutzer) {
        if (null == nutzer) {
            return;
        }

        nutzer.addVeranstaltung(this);
    }

    public void removeNutzer(Nutzer nutzer) {
        if (null == nutzer) {
            return;
        }

        nutzer.removeVeranstaltung(this);
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------

    @NotEmpty(message = "Veranstaltung muss mindestens eine/n Organisator/in haben")
    public List<Admin> organisatoren() {
        return nutzer.stream()
                .filter(u -> u instanceof Admin)
                .map(u -> (Admin) u)
                .toList();
    }

    public List<Teilnehmer> teilnehmer() {
        return nutzer.stream()
                .filter(u -> u instanceof Teilnehmer)
                .map(u -> (Teilnehmer) u)
                .toList();
    }

    public List<Referent> referenten() {
        return nutzer.stream()
                .filter(u -> u instanceof Referent)
                .map(u -> (Referent) u)
                .toList();
    }

    public List<Raum> getRaeume() {
        return gebaeude.stream()
                .flatMap(g -> g.getRaeume().stream())
                .toList();
    }
}