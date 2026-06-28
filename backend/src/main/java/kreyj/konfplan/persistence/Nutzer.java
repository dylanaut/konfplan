package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencsv.bean.CsvBindByName;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.CascadeType;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static kreyj.konfplan.persistence.NutzerVerfuegbarkeitId.nvId;

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

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "Nutzer_Veranstaltung",
            joinColumns = @JoinColumn(name = "nutzer_id"),
            inverseJoinColumns = @JoinColumn(name = "veranstaltung_id")
    )
    @JsonIgnoreProperties({"nutzer", "gebaeude", "slots"})
    private Set<Veranstaltung> veranstaltungen = new HashSet<>();

    public Set<Veranstaltung> getVeranstaltungen() {
        return Collections.unmodifiableSet(veranstaltungen);
    }

    public void addVeranstaltung(Veranstaltung v) {
        if (null == v) {
            return;
        }
        veranstaltungen.add(v);
        v.nutzer.add(this);

        if (this instanceof Referent || this instanceof Teilnehmer) {
            NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(this, v));
            Set<Long> slotIds = v.getSlotIds();
            if (null == nv) {
                nv = new NutzerVerfuegbarkeit(this, v, slotIds);
            } else {
                nv.setVerfuegbareSlotIds(slotIds);
            }
            nv.persist();
        }
    }

    public void removeVeranstaltung(Veranstaltung v) {
        if (null == v) {
            return;
        }

        veranstaltungen.remove(v);
        v.nutzer.remove(this);

        if (this instanceof Referent || this instanceof Teilnehmer) {
            NutzerVerfuegbarkeit.deleteById(nvId(this, v));
        }
    }

    // -------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------


    public NutzerVerfuegbarkeit getVerfuegbarkeit(Veranstaltung veranstaltung) {
        return NutzerVerfuegbarkeit.find("nutzerId = ?1 and veranstaltungId = ?2", getId(), veranstaltung.getId()).firstResult();
    }

    public void updateVerfuegbarkeit(Slot slot, Veranstaltung veranstaltung, boolean verfuegbar) {
        updateVerfuegbarkeit(slot, veranstaltung, verfuegbar, false);
    }


    public void updateVerfuegbarkeit(Slot slot, Veranstaltung veranstaltung, boolean verfuegbar, boolean createIfMissing) {
        Objects.requireNonNull(veranstaltung);
        Objects.requireNonNull(slot);

        NutzerVerfuegbarkeit nv = NutzerVerfuegbarkeit.findById(nvId(this, veranstaltung));

        if (null == nv) {
            if (createIfMissing) {
                nv = new NutzerVerfuegbarkeit(this, veranstaltung, veranstaltung.getSlotIds());
                nv.persist();
            } else {
                throw new IllegalStateException("Missing NutzerVerfuegbarkeit für " + this.getEmail()
                        + " in Veranstaltung '" + veranstaltung.getName() + "'");
            }
        }

        if (verfuegbar) {
            nv.addSlot(slot);
        } else {
            nv.removeSlot(slot);
        }

        nv.persist();
    }


    public static Nutzer findByEmail(String e) {
        return find("email", e).firstResult();
    }
}
