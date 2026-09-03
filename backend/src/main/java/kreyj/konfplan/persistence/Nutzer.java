package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencsv.bean.CsvBindByName;
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
import kreyj.konfplan.util.StringHelper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.NaturalId;

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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "role", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Organisator.class, name = "ORGANISATOR"),
    @JsonSubTypes.Type(value = Administrator.class, name = "ADMINISTRATOR"),
    @JsonSubTypes.Type(value = Referent.class, name = "REFERENT"),
    @JsonSubTypes.Type(value = Teilnehmer.class, name = "TEILNEHMER")
})
public abstract class Nutzer extends VersionedEntity {
    @NaturalId
    @Column(name = "login_name", unique = true, nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String loginName;

    @Column(unique = true)
    @CsvBindByName(column = "Email")
    private String email;

    // Verknuepfung zum Keycloak-User (Identitaet/Passwort liegen dort, nicht mehr lokal).
    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;

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


    public String getFullName() {
        return StringHelper.fullname(firstName, lastName);
    }


    public static Nutzer findByEmail(String e) {
        return find("email", e.trim().toLowerCase()).firstResult();
    }


    /**
     * Wie {@link #findByLoginName(String)}, faellt aber zusaetzlich auf eine Suche per E-Mail
     * zurueck. Verhindert, dass ein CSV-Import einen zweiten Nutzer mit derselben E-Mail (aber
     * einem anderen loginName, z.B. aus einem frueheren Import unter leicht abweichendem Namen)
     * anzulegen versucht - das wuerde erst am DB-weiten UNIQUE-Constraint auf email scheitern und
     * die laufende Transaktion "vergiften" statt kontrolliert zu ueberspringen.
     */
    public static Nutzer findByLoginNameOrEmail(String loginName, String email) {
        Nutzer byLoginName = findByLoginName(loginName);
        if (null != byLoginName) {
            return byLoginName;
        }
        return StringUtils.isBlank(email) ? null : findByEmail(email);
    }


    public void assignLoginName(String raw) {
        if (null != this.loginName) {
            throw new IllegalStateException("loginName ist bereits gesetzt und unveränderlich.");
        }
        this.loginName = normalizeLoginName(raw);
    }


    private static String normalizeLoginName(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new IllegalArgumentException("loginName darf nicht leer sein.");
        }
        return raw.trim().toLowerCase();
    }


    public static Nutzer findByLoginName(String loginName) {
        return find("loginName", normalizeLoginName(loginName)).firstResult();
    }
}
