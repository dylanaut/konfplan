package kreyj.vortragsmanager.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencsv.bean.CsvBindByName;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "User")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING)
@UserDefinition
// Jackson Magic: Erlaubt automatische Umwandlung von JSON in die richtige Unterklasse basierend auf "role"
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "role", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Admin.class, name = "ADMIN"),
        @JsonSubTypes.Type(value = Referent.class, name = "REFERENT"),
        @JsonSubTypes.Type(value = Teilnehmer.class, name = "TEILNEHMER")
})
public abstract class User extends SqliteEntity {

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

    @ManyToOne
    @JoinColumn(name = "veranstaltung_id", columnDefinition = "INTEGER")
    public Veranstaltung veranstaltung; // Relation zur Veranstaltung (Optional für Admins)

    @Version
    public Long version;

    public User() {
    }

    public static User findByEmail(String e) {
        return find("email", e).firstResult();
    }
}
