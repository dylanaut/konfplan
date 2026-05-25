package kreyj.konfplan.persistence;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Slot extends VersionedEntity {
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime startTime;
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime endTime;
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "veranstaltung_id", nullable = false, updatable = false)
    Veranstaltung veranstaltung;


    // -------------------------------------------------------------------
    // Konstruktoren
    // -------------------------------------------------------------------

    public Slot(String description, LocalDateTime startTime, LocalDateTime endTime) {
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}