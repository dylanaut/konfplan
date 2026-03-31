package kreyj.vortragsmanager.entity;

import com.opencsv.bean.CsvBindByName;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity

@UserDefinition
public class User extends SqliteEntity {
    @Column(unique = true)
    @Username
    @CsvBindByName(column = "email")
    public String email;

    @Password
    @Column(name = "password_hash")
    public String passwordHash;

    @Roles
    public String role;

    @Column(name = "first_name")
    @CsvBindByName(column = "vorname")
    public String firstName;

    @Column(name = "last_name")
    @CsvBindByName(column = "nachname")
    public String lastName;

    @Column(name = "organization")
    @CsvBindByName(column = "organisation")
    public String organization;

    @Column(name = "job_position")
    @CsvBindByName(column = "job_position")
    public String jobPosition;

    @Version
    public Long version;

    @Column(name = "is_active")
    public boolean isActive = true;

    @Column(name = "reset_token")
    public String resetToken;

    @Column(name = "reset_token_expiry")
    public LocalDateTime resetTokenExpiry;

    public User() {
    }

    public User(String email, String passwordHash, String role, String firstName, String lastName, boolean isActive) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isActive = isActive;
    }

    public static User findByEmail(String e) {
        return find("email", e).firstResult();
    }
}