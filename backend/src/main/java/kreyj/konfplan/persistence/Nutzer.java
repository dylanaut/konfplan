package kreyj.konfplan.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencsv.bean.CsvBindByName;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import kreyj.konfplan.util.StringHelper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.NaturalId;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    @JsonSubTypes.Type(value = Teilnehmer.class, name = "TEILNEHMER"),
    @JsonSubTypes.Type(value = Betrachter.class, name = "BETRACHTER")
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

    /**
     * Zusätzlich zur Primärrolle (Discriminator {@link #role}) gehaltene Rollen (siehe #751) -
     * z.B. ein Organisator, dem zusätzlich REFERENT zugewiesen wurde. Nur TEILNEHMER/REFERENT/
     * BETRACHTER sind als Zusatzrolle vergebbar (DB-seitig per CHECK-Constraint in
     * V33__nutzer_zusatzrollen.sql erzwungen). Prüfung/Mutation ausschließlich über
     * {@link #hatRolle(String)}/{@link #addZusatzrolle(String)}/{@link #removeZusatzrolle(String)} -
     * nie direkt über den Getter. Die Autorisierung (wer darf wem welche Zusatzrolle geben) liegt
     * bewusst NICHT hier, sondern in {@code OrganisatorService#validateGrantMatrix} - die Mutatoren
     * setzen nur die DB-Konsistenz voraus, dass der Aufrufer bereits geprüft hat, dass sowohl die
     * Rolle erlaubt ist als auch parallel {@code KeycloakUserProvisioningService#grantRealmRole}/
     * {@code #revokeRealmRole} aufgerufen wird, damit App-DB und Keycloak nicht divergieren.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutzer_zusatzrolle", joinColumns = @JoinColumn(name = "nutzer_id"))
    @Column(name = "rolle")
    private Set<String> zusatzRollen = new HashSet<>();

    // -------------------------------------------------------------------
    // Von Referent hochgezogen (siehe #751) - eine Person mit REFERENT als Zusatzrolle braucht
    // dieselben Felder wie eine primär als Referent angelegte Person.
    // -------------------------------------------------------------------

    @Column(name = "job_role")
    @CsvBindByName(column = "Position")
    private String jobRole;

    @Column(name = "organisation")
    @CsvBindByName(column = "Organisation")
    private String organisation;

    @OneToMany(mappedBy = "referent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    Set<Vortrag> vortraege = new HashSet<>();

    // -------------------------------------------------------------------
    // Von Teilnehmer hochgezogen (siehe #751)
    // -------------------------------------------------------------------

    /**
     * @deprecated Wird durch {@link #teilnehmerGruppenwerte} (strukturierte Gruppenkategorien,
     * siehe #690) abgelöst. Bleibt vorerst additiv bestehen, bis alle Konsumenten (Pflichtvortrag-
     * Matching, Reports, Frontend) umgestellt sind.
     */
    @Deprecated
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "teilnehmer_gruppen", joinColumns = @JoinColumn(name = "teilnehmer_id"))
    @Column(name = "gruppen")
    private Set<String> gruppen = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "teilnehmer_gruppenwert",
        joinColumns = @JoinColumn(name = "teilnehmer_id"),
        inverseJoinColumns = @JoinColumn(name = "gruppenkategoriewert_id")
    )
    @Setter(AccessLevel.NONE)
    private Set<GruppenkategorieWert> teilnehmerGruppenwerte = new HashSet<>();

    @OneToMany(mappedBy = "teilnehmer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    private Set<Prioritaet> prioritaeten = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "teilnehmer_neigungen", joinColumns = @JoinColumn(name = "teilnehmer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "neigung", length = 50)
    private Set<Neigung> neigungen = new HashSet<>();

    // -------------------------------------------------------------------
    // Von Betrachter hochgezogen (siehe #751)
    // -------------------------------------------------------------------

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "betrachter_gruppenwert",
        joinColumns = @JoinColumn(name = "betrachter_id"),
        inverseJoinColumns = @JoinColumn(name = "gruppenkategoriewert_id")
    )
    @Setter(AccessLevel.NONE)
    private Set<GruppenkategorieWert> betrachterGruppenwerte = new HashSet<>();


    public Set<Veranstaltung> getVeranstaltungen() {
        return Collections.unmodifiableSet(veranstaltungen);
    }


    public void addVeranstaltung(Veranstaltung v) {
        if (null == v) {
            return;
        }
        veranstaltungen.add(v);
        v.nutzer.add(this);

        // Betrachter ist absichtlich ausgenommen: er nimmt an keiner Planung/Verfuegbarkeit
        // teil, sondern liest nur die Daten der ihm zugewiesenen Gruppen.
        if (hatRolle("REFERENT") || hatRolle("TEILNEHMER")) {
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

        if (hatRolle("REFERENT") || hatRolle("TEILNEHMER")) {
            NutzerVerfuegbarkeit.deleteById(nvId(this, v));
        }
    }

    // -------------------------------------------------------------------
    // Zusatzrollen (siehe #751)
    // -------------------------------------------------------------------


    public Set<String> getZusatzRollen() {
        return Collections.unmodifiableSet(zusatzRollen);
    }


    /**
     * Prüft, ob dieser Nutzer als die angegebene Rolle handeln darf - entweder weil es seine
     * Primärrolle ist, weil sie ihm als Zusatzrolle zugewiesen wurde, oder weil ein Administrator
     * (Java-Vererbung: {@code Administrator extends Organisator}) implizit auch als ORGANISATOR
     * gilt. Das ist die einzig korrekte Prüfung für Selbstbedienungs-Autorisierung - ein
     * {@code instanceof}-Check erfasst nur die Primärrolle.
     */
    public boolean hatRolle(String rolle) {
        if (null == rolle) {
            return false;
        }
        return rolle.equals(role) || zusatzRollen.contains(rolle) || ("ORGANISATOR".equals(rolle) && "ADMINISTRATOR".equals(role));
    }


    public void addZusatzrolle(String rolle) {
        zusatzRollen.add(rolle);
    }


    public void removeZusatzrolle(String rolle) {
        zusatzRollen.remove(rolle);
    }

    // -------------------------------------------------------------------
    // Referent-Fachdaten (siehe #751)
    // -------------------------------------------------------------------


    public Set<Vortrag> getVortraege() {
        return Collections.unmodifiableSet(vortraege);
    }


    public void addVortrag(Vortrag aVortrag) {
        if (null == aVortrag) {
            return;
        }

        vortraege.add(aVortrag);
        aVortrag.referent = this;
    }


    public void removeVortrag(Vortrag aVortrag) {
        if (null == aVortrag) {
            return;
        }

        vortraege.remove(aVortrag);
        aVortrag.referent = null;
    }

    // -------------------------------------------------------------------
    // Teilnehmer-Fachdaten (siehe #751)
    // -------------------------------------------------------------------


    public Set<String> getGruppen() {
        return Collections.unmodifiableSet(gruppen);
    }


    public void addGruppe(String gruppe) {
        if (StringUtils.isBlank(gruppe)) {
            return;
        }
        gruppen.add(gruppe);
    }


    public void removeGruppe(String gruppe) {
        if (null == gruppe) {
            return;
        }
        gruppen.remove(gruppe);
    }


    public void setGruppen(Collection<String> neueGruppen) {
        gruppen.clear();
        if (null != neueGruppen) {
            neueGruppen.forEach(this::addGruppe);
        }
    }


    public Set<GruppenkategorieWert> getTeilnehmerGruppenwerte() {
        return Collections.unmodifiableSet(teilnehmerGruppenwerte);
    }


    public boolean hatGruppenwert(GruppenkategorieWert wert) {
        return null != wert && teilnehmerGruppenwerte.contains(wert);
    }


    /**
     * Ordnet dem Teilnehmer einen Gruppenwert zu. Ist die Kategorie nicht
     * {@link Gruppenkategorie#isMehrwertig() mehrwertig}, ersetzt der neue Wert einen ggf. bereits
     * zugeordneten Wert derselben Kategorie (max. ein Wert pro einwertiger Kategorie).
     */
    public void addTeilnehmerGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        if (!wert.getGruppenkategorie().isMehrwertig()) {
            teilnehmerGruppenwerte.removeIf(vorhandener -> {
                boolean gleicheKategorie = vorhandener.getGruppenkategorie().getId().equals(wert.getGruppenkategorie().getId());
                if (gleicheKategorie) {
                    vorhandener.entferneTeilnehmer(this);
                }
                return gleicheKategorie;
            });
        }
        teilnehmerGruppenwerte.add(wert);
        wert.nimmTeilnehmerAuf(this);
    }


    public void removeTeilnehmerGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        if (teilnehmerGruppenwerte.remove(wert)) {
            wert.entferneTeilnehmer(this);
        }
    }


    /**
     * Prüft die Gruppenmitgliedschaft gegen BEIDE Modelle: das bisherige flache {@link #gruppen}
     * und die neuen strukturierten {@link #teilnehmerGruppenwerte} (siehe #690) - damit
     * Pflichtvortrag-Zuordnungen unabhängig davon greifen, über welches der beiden Systeme einem
     * Teilnehmer diese Gruppe zugewiesen wurde. {@code teilnehmerGruppenwerte} wird dabei nur
     * innerhalb derselben Veranstaltung berücksichtigt (ein Wert gehört zu genau einer
     * Gruppenkategorie einer Veranstaltung).
     */
    public boolean istInGruppe(String gruppenName, Veranstaltung veranstaltung) {
        if (null == gruppenName) {
            return false;
        }
        if (gruppen.contains(gruppenName)) {
            return true;
        }
        return teilnehmerGruppenwerte.stream().anyMatch(wert -> wert.getWert().equals(gruppenName)
            && wert.getGruppenkategorie().getVeranstaltung().getId().equals(veranstaltung.getId()));
    }


    public Set<Prioritaet> getPrioritaeten() {
        return Collections.unmodifiableSet(prioritaeten);
    }


    public void addPrioritaet(Prioritaet prioritaet) {
        if (null == prioritaet) {
            return;
        }
        prioritaeten.add(prioritaet);
    }


    public void removePrioritaet(Prioritaet prioritaet) {
        if (null == prioritaet) {
            return;
        }
        prioritaeten.remove(prioritaet);
    }


    public Set<Neigung> getNeigungen() {
        return Collections.unmodifiableSet(neigungen);
    }


    public void setNeigungen(Set<Neigung> neueNeigungen) {
        neigungen.clear();
        if (null != neueNeigungen) {
            neigungen.addAll(neueNeigungen);
        }
    }

    // -------------------------------------------------------------------
    // Betrachter-Fachdaten (siehe #751)
    // -------------------------------------------------------------------


    public Set<GruppenkategorieWert> getBetrachterGruppenwerte() {
        return Collections.unmodifiableSet(betrachterGruppenwerte);
    }


    public void addBetrachterGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        betrachterGruppenwerte.add(wert);
        wert.nimmBetrachterAuf(this);
    }


    public void removeBetrachterGruppenwert(GruppenkategorieWert wert) {
        if (null == wert) {
            return;
        }
        if (betrachterGruppenwerte.remove(wert)) {
            wert.entferneBetrachter(this);
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


    /**
     * Nutzer, die als Rolle {@code rolle} handeln dürfen - Primärrolle ODER Zusatzrolle (siehe
     * {@link #hatRolle(String)}). Anders als eine Abfrage über die konkrete Subklasse (z.B.
     * {@code Teilnehmer.find(...)}, die nur die Primärrolle erfasst) berücksichtigt dies auch
     * Nutzer, denen {@code rolle} nur zusätzlich zugewiesen wurde.
     */
    public static List<Nutzer> findByRolle(String rolle) {
        return Nutzer.<Nutzer>find("role = ?1 or ?1 member of zusatzRollen", rolle).list();
    }
}
