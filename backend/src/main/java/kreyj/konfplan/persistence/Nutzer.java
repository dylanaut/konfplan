package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencsv.bean.CsvBindByName;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING)
@UserDefinition
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "role", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Admin.class, name = "ADMIN"),
        @JsonSubTypes.Type(value = Referent.class, name = "REFERENT"),
        @JsonSubTypes.Type(value = Teilnehmer.class, name = "TEILNEHMER")
})
public abstract class Nutzer extends VersionedEntity {
    @NaturalId(mutable = true)
    @Column(unique = true)
    @Username
    @CsvBindByName(column = "Email")
    private String email;

    @Password
    @Column(name = "password_hash")
    private String passwordHash;

    @Roles
    @Column(name = "role", insertable = false, updatable = false)
    private String role;

    @Column(name = "first_name")
    @CsvBindByName(column = "Vorname")
    private String firstName;

    @Column(name = "last_name")
    @CsvBindByName(column = "Nachname")
    private String lastName;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    // Felder für E-Mail-Adressänderung
    @Column(name = "new_email")
    private String newEmail;

    @Column(name = "email_change_token")
    private String emailChangeToken;

    @Column(name = "email_change_token_expiry")
    private LocalDateTime emailChangeTokenExpiry;

    @ManyToMany
    @JoinTable(
            name = "Nutzer_Veranstaltung",
            joinColumns = @JoinColumn(name = "nutzer_id"),
            inverseJoinColumns = @JoinColumn(name = "veranstaltung_id")
    )
    @JsonIgnoreProperties({"nutzer", "gebaeude", "eventSlots"})
    private Set<Veranstaltung> veranstaltungen = new HashSet<>();

    public Set<Veranstaltung> getVeranstaltungen() {
        return Collections.unmodifiableSet(veranstaltungen);
    }

    public void addVeranstaltung(Veranstaltung v) {
        if (null == v) {
            return;
        }
        if (veranstaltungen.add(v)) {
            v.nutzer.add(this);
        }

        //TODO remove business logic
        if (this instanceof Referent || this instanceof Teilnehmer) {
            for (EventSlot slot : v.getEventSlots()) {
                if (Verfuegbarkeit.count("nutzer = ?1 and slot = ?2", this, slot) == 0) {
                    new Verfuegbarkeit(this, slot, true).persist();

                    this.addVerfuegbarenSlot(slot);
                }
            }
        }
    }

    public void removeVeranstaltung(Veranstaltung v) {
        if (null == v) {
            return;
        }

        if (veranstaltungen.remove(v)) {
            v.nutzer.remove(this);
        }

// TODO remove business logic
        if (this instanceof Referent || this instanceof Teilnehmer) {
            for (EventSlot slot : v.getEventSlots()) {
                Verfuegbarkeit.delete("nutzer = ?1 and slot = ?2", this, slot);
            }
        }
    }

    @ManyToMany
    @JoinTable(
            name = "Teilnehmer_EventSlot",
            joinColumns = @JoinColumn(name = "teilnehmer_id"),
            inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    @JsonIgnore // Add this annotation to ignore during JSON serialization
    Set<EventSlot> verfuegbareSlots = new HashSet<>();

    public Set<EventSlot> getVerfuegbareSlots() {
        return Collections.unmodifiableSet(verfuegbareSlots);
    }

    public void addVerfuegbarenSlot(EventSlot slot) {
        if (null == slot) {
            return;
        }

        if (!this.getVeranstaltungen().contains(slot.getVeranstaltung())) {
            throw new IllegalArgumentException("Der Teilnehmer ist nicht für die Veranstaltung des EventSlots angemeldet.");
        }

        if (verfuegbareSlots.add(slot)) {
            slot.nutzer.add(this);
        }
    }

    public void removeVerfuegbarenSlot(EventSlot eventSlot) {
        if (null == eventSlot) {
            return;
        }

        if (verfuegbareSlots.remove(eventSlot)) {
            eventSlot.nutzer.remove(this);
        }
    }

    public void clearVerfuegbareSlots() {
        for (EventSlot slot : new ArrayList<>(verfuegbareSlots)) {
            removeVerfuegbarenSlot(slot);
        }
    }

    public static Nutzer findByEmail(String e) {
        return find("email", e).firstResult();
    }
}