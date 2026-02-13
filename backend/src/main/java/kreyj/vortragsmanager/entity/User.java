package kreyj.vortragsmanager.entity;

import com.opencsv.bean.CsvBindByName;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.security.jpa.*;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@UserDefinition
public class User extends PanacheEntity {
    @Column(unique = true)
    @Username
    @CsvBindByName(column = "Email")
    public String email;

    @Password
    @Column(name = "password_hash")
    public String passwordHash;

    @Roles
    public String role;

    @CsvBindByName(column = "Vorname")
    public String firstName;

    @CsvBindByName(column = "Nachname")
    public String lastName;

    @CsvBindByName(column = "Organisation")
    public String organization;

    @CsvBindByName(column = "Position")
    public String jobRole;

    @Version
    public Long version;

    public boolean isActive = true;
    public String resetToken;
    public LocalDateTime resetTokenExpiry;

    public static User findByEmail(String e) {
        return find("email", e).firstResult();
    }
}