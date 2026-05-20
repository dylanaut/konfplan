package kreyj.konfplan.persistence;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class EventSlot extends VersionedEntity {
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime startTime;
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime endTime;
    private String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "veranstaltung_id", nullable = false, updatable = false)
    private Veranstaltung veranstaltung;

    public EventSlot() {
    }

    public EventSlot(String description, LocalDateTime startTime, LocalDateTime endTime) {
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @ManyToMany(mappedBy = "verfuegbareSlots")
    private Set<Nutzer> nutzer = new HashSet<>();

    public Set<Nutzer> getNutzer() {
        return Collections.unmodifiableSet(nutzer);
    }

    public void addNutzer(Nutzer nutzer) {
        if (!nutzer.getVeranstaltungen().contains(this.veranstaltung)) {
            throw new IllegalArgumentException("Der Teilnehmer ist nicht für die Veranstaltung angemeldet.");
        }

        if (!this.nutzer.contains(nutzer)) {
            this.nutzer.add(nutzer);
            nutzer.addVerfuegbarenSlot(this);
        }
    }

    public void removeNutzer(Nutzer nutzer) {
        if (this.nutzer == null) {
            this.nutzer = new HashSet<>();
        }

        if (!nutzer.getVeranstaltungen().contains(this.veranstaltung)) {
            throw new IllegalArgumentException("Der Teilnehmer ist nicht für die Veranstaltung angemeldet.");
        }

        if (this.nutzer.contains(nutzer)) {
            this.nutzer.remove(nutzer);
            nutzer.removeVerfuegbarenSlot(this);
        } else {
            throw new IllegalArgumentException("Der Teilnehmer ist nicht in diesem EventSlot enthalten.");
        }
    }

    public void clearNutzer() {
        for (Nutzer nutzer : new HashSet<>(this.nutzer)) {
            removeNutzer(nutzer);
        }
    }

    public Set<Teilnehmer> getTeilnehmer() {
        Set<Teilnehmer> teilnehmer = new HashSet<>();
        for (Nutzer nutzer : nutzer) {
            if (nutzer instanceof Teilnehmer) {
                teilnehmer.add((Teilnehmer) nutzer);
            }
        }

        return Collections.unmodifiableSet(teilnehmer);
    }

    public Set<Referent> getReferenten() {
        Set<Referent> referenten = new HashSet<>();
        for (Nutzer nutzer : nutzer) {
            if (nutzer instanceof Referent) {
                referenten.add((Referent) nutzer);
            }
        }

        return Collections.unmodifiableSet(referenten);
    }
}