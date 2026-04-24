package kreyj.vortragsmanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencsv.bean.CsvBindByName;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
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

    @Column(unique = true)
    @Username
    @CsvBindByName(column = "Email")
    public String email;

    @Password
    @Column(name = "password_hash")
    public String passwordHash;

    @Roles
    @Column(name = "role", insertable = false, updatable = false)
    public String role;

    @Column(name = "first_name")
    @CsvBindByName(column = "Vorname")
    public String firstName;

    @Column(name = "last_name")
    @CsvBindByName(column = "Nachname")
    public String lastName;

    @Column(name = "is_active")
    public boolean isActive = true;

    @Column(name = "reset_token")
    public String resetToken;

    @Column(name = "reset_token_expiry")
    public LocalDateTime resetTokenExpiry;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "Nutzer_Veranstaltung",
            joinColumns = @JoinColumn(name = "nutzer_id"),
            inverseJoinColumns = @JoinColumn(name = "veranstaltung_id")
    )
    @JsonIgnoreProperties({"nutzer", "gebaeude", "eventSlots"})
    public Set<Veranstaltung> veranstaltungen = new HashSet<>();

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
        v.nutzer.add(this);

        if (this instanceof Referent || this instanceof Teilnehmer) {
            for (EventSlot slot : v.eventSlots) {
                if (Verfuegbarkeit.count("nutzer = ?1 and slot = ?2", this, slot) == 0) {
                    Verfuegbarkeit verfuegbarkeit = new Verfuegbarkeit();
                    verfuegbarkeit.nutzer = this;
                    verfuegbarkeit.slot = slot;
                    verfuegbarkeit.isAvailable = true;
                    verfuegbarkeit.persist();
                }
            }
        }
    }

    public void removeVeranstaltung(Veranstaltung v) {
        this.veranstaltungen.remove(v);
        v.nutzer.remove(this);

        if (this instanceof Referent || this instanceof Teilnehmer) {
            for (EventSlot slot : v.eventSlots) {
                Verfuegbarkeit.delete("nutzer = ?1 and slot = ?2", this, slot);
            }
        }
    }
}
