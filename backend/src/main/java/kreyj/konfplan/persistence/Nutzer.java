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
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
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

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "Nutzer_Veranstaltung",
            joinColumns = @JoinColumn(name = "nutzer_id"),
            inverseJoinColumns = @JoinColumn(name = "veranstaltung_id")
    )
    @JsonIgnoreProperties({"nutzer", "gebaeude", "eventSlots"})
    private Set<Veranstaltung> veranstaltungen = new HashSet<>();
    @ManyToMany
    @JoinTable(
            name = "Teilnehmer_EventSlot",
            joinColumns = @JoinColumn(name = "teilnehmer_id"),
            inverseJoinColumns = @JoinColumn(name = "eventslot_id")
    )
    @JsonIgnore // Add this annotation to ignore during JSON serialization
    private List<EventSlot> verfuegbareSlots = new ArrayList<>();

    public Set<Veranstaltung> getVeranstaltungen() {
        return Collections.unmodifiableSet(veranstaltungen);
    }

    public Nutzer() {
    }

    public static Nutzer findByEmail(String e) {
        return find("email", e).firstResult();
    }

    public void addVeranstaltung(Veranstaltung v) {
        if (this.veranstaltungen.contains(v)) {
            return;
        }
        this.veranstaltungen.add(v);
        v.addNutzer(this);

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
        this.veranstaltungen.remove(v);

        if (v.getNutzer().contains(this)) {
            v.removeNutzer(this);
        }

        if (this instanceof Referent || this instanceof Teilnehmer) {
            for (EventSlot slot : v.getEventSlots()) {
                Verfuegbarkeit.delete("nutzer = ?1 and slot = ?2", this, slot);
            }
        }
    }

    public List<EventSlot> getVerfuegbareSlots() {
        return Collections.unmodifiableList(verfuegbareSlots);
    }

    public void addVerfuegbarenSlot(EventSlot slot) {
        if (!this.getVeranstaltungen().contains(slot.getVeranstaltung())) {
            throw new IllegalArgumentException("Der Teilnehmer ist nicht für die Veranstaltung des EventSlots angemeldet.");
        }

        if (!verfuegbareSlots.contains(slot)) {
            verfuegbareSlots.add(slot);
            slot.addNutzer(this);
        }
    }

    public void removeVerfuegbarenSlot(EventSlot eventSlot) {
        if (verfuegbareSlots.contains(eventSlot)) {
            this.verfuegbareSlots.remove(eventSlot);
        }

        if (eventSlot.getNutzer().contains(this)) {
            eventSlot.removeNutzer(this);
        }
    }

    public void clearVerfuegbareSlots() {
        for (EventSlot slot : new ArrayList<>(verfuegbareSlots)) {
            removeVerfuegbarenSlot(slot);
        }
    }
}