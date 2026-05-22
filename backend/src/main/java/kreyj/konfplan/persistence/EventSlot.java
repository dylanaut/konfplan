package kreyj.konfplan.persistence;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
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
public class EventSlot extends VersionedEntity {
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime startTime;
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime endTime;
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "veranstaltung_id", nullable = false, updatable = false)
    @Setter(AccessLevel.PRIVATE)
    Veranstaltung veranstaltung;


    @ManyToMany(mappedBy = "verfuegbareSlots")
    Set<Nutzer> nutzer = new HashSet<>();

    public Set<Nutzer> getNutzer() {
        return Collections.unmodifiableSet(nutzer);
    }


    @ManyToMany(mappedBy = "verfuegbareSlots")
    Set<Raum> raeume = new HashSet<>();

    public Set<Raum> getRaeume() {
        return Collections.unmodifiableSet(raeume);
    }


    public EventSlot(String description, LocalDateTime startTime, LocalDateTime endTime) {
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
    }


    public Set<Teilnehmer> getTeilnehmer() {
        return nutzer.stream().filter(n -> n instanceof Teilnehmer)
                .map(n -> (Teilnehmer) n)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<Referent> getReferenten() {
        return nutzer.stream().filter(n -> n instanceof Referent)
                .map(n -> (Referent) n)
                .collect(Collectors.toUnmodifiableSet());
    }
}