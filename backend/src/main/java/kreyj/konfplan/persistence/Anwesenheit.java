package kreyj.konfplan.persistence;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kreyj.konfplan.persistence.converter.LocalDateTimeConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Erfasst, dass ein Teilnehmer sich per QR-Code-Scan für einen Slot als anwesend registriert hat
 * (siehe #735) - unabhängig davon, ob er für den dort laufenden Vortrag überhaupt eingeplant war
 * (das wird erst bei der Auswertung mit dem geplanten Zuweisungs-Stand verglichen). Ein Teilnehmer
 * hat höchstens eine Zeile pro Slot; erneutes Scannen (z.B. in einem anderen Raum) aktualisiert
 * {@link #raum}/{@link #eingechecktAm} statt eine zweite Zeile anzulegen (siehe
 * AnwesenheitService#checkIn).
 */
@Entity
// Siehe Prioritaet.java fuer denselben Grund: zusaetzlich zur Flyway-Migration
// (V31__anwesenheit.sql) noetig, damit dieselbe Constraint auch in Dev/Test greift, wo Hibernate
// das Schema aus den Entities generiert statt Flyway-Migrationen anzuwenden.
@Table(uniqueConstraints = @UniqueConstraint(name = "UK_anwesenheit_teilnehmer_slot", columnNames = {"teilnehmer_id", "slot_id"}))
@NoArgsConstructor
@Getter
@Setter
public class Anwesenheit extends VersionedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Nutzer teilnehmer; // Nutzer statt Teilnehmer-Subtyp seit #751, siehe Vortrag.referent

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Veranstaltung veranstaltung;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Raum raum;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime eingechecktAm;


    public Anwesenheit(Nutzer teilnehmer, Veranstaltung veranstaltung, Slot slot, Raum raum, LocalDateTime eingechecktAm) {
        this.teilnehmer = teilnehmer;
        this.veranstaltung = veranstaltung;
        this.slot = slot;
        this.raum = raum;
        this.eingechecktAm = eingechecktAm;
    }


    public static Anwesenheit findByTeilnehmerUndSlot(Nutzer teilnehmer, Slot slot) {
        return find("teilnehmer = ?1 and slot = ?2", teilnehmer, slot).firstResult();
    }
}
