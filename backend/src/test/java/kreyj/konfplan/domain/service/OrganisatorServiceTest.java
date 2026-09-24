package kreyj.konfplan.domain.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import kreyj.konfplan.adapter.in.web.DatabaseCleaner;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.domain.exception.CreateVortragException;
import kreyj.konfplan.domain.exception.UpdateNutzerException;
import kreyj.konfplan.domain.exception.UpdateVortragException;
import kreyj.konfplan.persistence.Administrator;
import kreyj.konfplan.persistence.Betrachter;
import kreyj.konfplan.persistence.Gruppenkategorie;
import kreyj.konfplan.persistence.GruppenkategorieWert;
import kreyj.konfplan.persistence.IdEntity;
import kreyj.konfplan.persistence.Nachricht;
import kreyj.konfplan.persistence.Organisator;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Prioritaet;
import kreyj.konfplan.persistence.Protokoll;
import kreyj.konfplan.persistence.ProtokollKategorie;
import kreyj.konfplan.persistence.Referent;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import kreyj.konfplan.persistence.Wahlvortrag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
public class OrganisatorServiceTest extends DatabaseCleaner {

    @Inject
    OrganisatorService organisatorService;

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    private Long testUserId;
    private Veranstaltung veranstaltung;
    private Long tnId;

    @BeforeEach
    @Transactional
    public void setUp() {
        // Aufraeumen erledigt DatabaseCleaner.cleanDatabase() (laeuft als Superklassen-@BeforeEach
        // bereits vor dieser Methode) - u.a. wichtig, damit Vortrag-Zeilen mit einem
        // referent_id-FK auf Nutzer vor dessen Loeschung entfernt sind.

        // Create a test user
        Nutzer user = new Organisator();
        user.assignLoginName("testexample");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.persist();
        testUserId = user.getId();

        // Create a test event
        veranstaltung = new Veranstaltung();
        veranstaltung.setName("Test Konferenz");
        veranstaltung.setBeginntAm(LocalDateTime.now());
        veranstaltung.setEndetAm(LocalDateTime.now().plusDays(1));
        veranstaltung.persist();

        // Create a test participant
        Teilnehmer teilnehmer = new Teilnehmer();
        teilnehmer.assignLoginName("teilnehmerexample");
        teilnehmer.setEmail("teilnehmer@example.com");
        teilnehmer.setFirstName("Max");
        teilnehmer.setLastName("Mustermann");
        teilnehmer.persist();
        teilnehmer.addVeranstaltung(veranstaltung);

        tnId = teilnehmer.getId();
    }

    @Test
    public void testCreateAndGetGruppen() {
        organisatorService.createGruppe(veranstaltung.getId(), "Gruppe A");
        List<String> gruppen = organisatorService.getGruppen(veranstaltung.getId());
        assertThat(gruppen.contains("Gruppe A")).isTrue();
        assertThat(gruppen.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void testCreateGruppe_DuplicateName_ThrowsException() {
        organisatorService.createGruppe(veranstaltung.getId(), "Gruppe A");
        assertThatExceptionOfType(CreateVortragException.class).isThrownBy(() -> organisatorService.createGruppe(veranstaltung.getId(), "Gruppe A"));
    }

    @Test
    @Transactional
    public void testRenameGruppe() {
        // Setup
        organisatorService.createGruppe(veranstaltung.getId(), "Gruppe A");
        Teilnehmer tn = Teilnehmer.findById(tnId);
        tn.addGruppe("Gruppe A");
        tn.persist();

        // Rename
        organisatorService.renameGruppe(veranstaltung.getId(), "Gruppe A", "Gruppe B");

        // Verify
        Veranstaltung updatedVeranstaltung = Veranstaltung.findById(veranstaltung.getId());
        assertThat(updatedVeranstaltung.getGruppen().contains("Gruppe A")).isFalse();
        assertThat(updatedVeranstaltung.getGruppen().contains("Gruppe B")).isTrue();

        Teilnehmer updatedTeilnehmer = Teilnehmer.findById(tnId);
        assertThat(updatedTeilnehmer.getGruppen().contains("Gruppe A")).isFalse();
        assertThat(updatedTeilnehmer.getGruppen().contains("Gruppe B")).isTrue();
    }

    @Test
    public void testRenameGruppe_ToExistingName_ThrowsException() {
        organisatorService.createGruppe(veranstaltung.getId(), "Gruppe A");
        organisatorService.createGruppe(veranstaltung.getId(), "Gruppe B");
        assertThatExceptionOfType(UpdateVortragException.class).isThrownBy(() -> organisatorService.renameGruppe(veranstaltung.getId(), "Gruppe A", "Gruppe B"));
    }

    @Test
    @Transactional
    public void testDeleteGruppe() {
        // Setup
        Teilnehmer tn = Teilnehmer.findById(tnId);
        organisatorService.createGruppe(veranstaltung.getId(), "Gruppe A");
        tn.addGruppe("Gruppe A");

        // Delete
        organisatorService.deleteGruppe(veranstaltung.getId(), "Gruppe A");

        // Verify
        Veranstaltung updatedVeranstaltung = Veranstaltung.findById(veranstaltung.getId());
        assertThat(updatedVeranstaltung.getGruppen().contains("Gruppe A")).isFalse();

        Teilnehmer updatedTeilnehmer = Teilnehmer.findById(tnId);
        assertThat(updatedTeilnehmer.getGruppen().contains("Gruppe A")).isFalse();
    }

    @Test
    @Transactional
    public void testGetGruppenTeilnehmer() {
        // Setup
        Teilnehmer tn = Teilnehmer.findById(tnId);

        organisatorService.createGruppe(veranstaltung.getId(), "Gruppe A");
        tn.addGruppe("Gruppe A");
//        tn.persist();

        // Test
        List<Teilnehmer> result = Teilnehmer.getGruppenTeilnehmer("Gruppe A", veranstaltung);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().getId()).isEqualTo(tnId);

        // Test with non-existent group
        List<Teilnehmer> emptyResult = Teilnehmer.getGruppenTeilnehmer("Gruppe B", veranstaltung);
        assertThat(emptyResult.isEmpty()).isTrue();
    }


    // Regression: ein Organisator-Konto ohne E-Mail-Adresse kann sich bei vergessenem Passwort nicht
    // selbst wiederherstellen (Keycloaks Passwort-Reset braucht eine E-Mail-Adresse) und es gab
    // bislang auch keinen anderen Weg (siehe testResetPassword_* unten für den Rettungsweg).
    @Test
    public void testCreateUser_OrganisatorWithoutEmail_ThrowsException() {
        NutzerDto dto = new NutzerDto("ORGANISATOR", null, "Ohne", "Email", true);
        dto.loginName = "ohne.email";

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> organisatorService.createUser(dto, null, null));

        assertThat(Nutzer.findByLoginName("ohne.email")).isNull();
    }


    @Test
    public void testCreateUser_OrganisatorWithEmail_Succeeds() {
        NutzerDto dto = new NutzerDto("ORGANISATOR", "mit.email@test.de", "Mit", "Email", true);
        dto.loginName = "mit.email";

        NutzerDto created = organisatorService.createUser(dto, null, null);

        assertThat(created.email).isEqualTo("mit.email@test.de");
    }


    @Test
    public void testUpdateUser_RemovingOrganisatorEmail_ThrowsException() {
        NutzerDto dto = NutzerDto.from(Nutzer.findById(testUserId));
        dto.email = null;

        assertThatExceptionOfType(UpdateNutzerException.class)
            .isThrownBy(() -> organisatorService.updateUser(testUserId, dto, null));

        assertThat(Nutzer.<Nutzer>findById(testUserId).getEmail()).isEqualTo("test@example.com");
    }


    @Test
    public void testResetPassword_Success() {
        boolean result = organisatorService.resetPassword(veranstaltung.getId(), testUserId, "einNeuesPasswort123!");

        assertThat(result).isTrue();
        Nutzer updated = Nutzer.findById(testUserId);
        verify(keycloakUserProvisioningService).resetPassword(eq(updated), eq("einNeuesPasswort123!"));
    }


    @Test
    public void testResetPassword_UnknownUser_ReturnsFalse() {
        boolean result = organisatorService.resetPassword(veranstaltung.getId(), -1L, "einNeuesPasswort123!");

        assertThat(result).isFalse();
    }


    @Test
    public void testResetPassword_PasswordTooShort_ThrowsException() {
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> organisatorService.resetPassword(veranstaltung.getId(), testUserId, "kurz"));
    }


    @Test
    public void testCreateUser_ZweiTeilnehmerOhneEmail_BeideErfolgreich() {
        // Regression fuer #282: ein Leerstring statt null im email-Feld verletzte beim zweiten
        // Nutzer ohne E-Mail-Adresse den DB-UNIQUE-Constraint (NULL ist davon ausgenommen, "" nicht).
        NutzerDto ersterDto = new NutzerDto("TEILNEHMER", "", "Erster", "Teilnehmer", true);
        ersterDto.loginName = "erster.ohne.email";
        NutzerDto zweiterDto = new NutzerDto("TEILNEHMER", "", "Zweiter", "Teilnehmer", true);
        zweiterDto.loginName = "zweiter.ohne.email";

        NutzerDto ersterCreated = organisatorService.createUser(ersterDto, null, null);
        NutzerDto zweiterCreated = organisatorService.createUser(zweiterDto, null, null);

        assertThat(ersterCreated.email).isNull();
        assertThat(zweiterCreated.email).isNull();
        assertThat(Nutzer.<Nutzer>findById(ersterCreated.id).getEmail()).isNull();
        assertThat(Nutzer.<Nutzer>findById(zweiterCreated.id).getEmail()).isNull();
    }


    @Test
    public void testCreateUser_TeilnehmerWithNeigungen_Succeeds() {
        NutzerDto dto = new NutzerDto("TEILNEHMER", "neu@example.com", "Neue", "Person", true);
        dto.loginName = "neue.person";
        dto.neigungen = Set.of(Neigung.SOZIAL, Neigung.WISSENSCHAFTLICH);

        NutzerDto created = organisatorService.createUser(dto, null, null);

        assertThat(created.neigungen).containsExactlyInAnyOrder(Neigung.SOZIAL, Neigung.WISSENSCHAFTLICH);
        Teilnehmer persisted = Teilnehmer.findById(created.id);
        assertThat(persisted.getNeigungen()).containsExactlyInAnyOrder(Neigung.SOZIAL, Neigung.WISSENSCHAFTLICH);
    }


    @Test
    @Transactional
    public void testUpdateUser_TeilnehmerNeigungen_ReplacesExistingSet() {
        Teilnehmer tn = Teilnehmer.findById(tnId);
        NutzerDto dto = NutzerDto.from(tn);
        dto.neigungen = Set.of(Neigung.KREATIV, Neigung.MEDIZINISCH);
        organisatorService.updateUser(tnId, dto, null);

        Teilnehmer updated = Teilnehmer.findById(tnId);
        assertThat(updated.getNeigungen()).containsExactlyInAnyOrder(Neigung.KREATIV, Neigung.MEDIZINISCH);

        // Ein zweites Update mit anderer Auswahl muss die vorherige Auswahl vollstaendig ersetzen
        // (Checkbox-UI: ein Entfernen einzelner Werte muss moeglich sein).
        NutzerDto dto2 = NutzerDto.from(Teilnehmer.findById(tnId));
        dto2.neigungen = Set.of(Neigung.TECHNISCH);
        organisatorService.updateUser(tnId, dto2, null);

        Teilnehmer updated2 = Teilnehmer.findById(tnId);
        assertThat(updated2.getNeigungen()).containsExactly(Neigung.TECHNISCH);
    }


    @Test
    @Transactional
    public void testUpdateUser_TeilnehmerGruppenwerteByKategorie_ReplacesExistingSet() {
        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", true, false);
        klasse.persist();
        GruppenkategorieWert gruppeA = new GruppenkategorieWert(klasse, "Gruppe A");
        gruppeA.persist();
        GruppenkategorieWert gruppeB = new GruppenkategorieWert(klasse, "Gruppe B");
        gruppeB.persist();
        GruppenkategorieWert gruppeC = new GruppenkategorieWert(klasse, "Gruppe C");
        gruppeC.persist();

        Teilnehmer tn = Teilnehmer.findById(tnId);
        tn.addTeilnehmerGruppenwert(gruppeA);
        tn.addTeilnehmerGruppenwert(gruppeB);

        // Bearbeiten-Dialog: "Gruppe B" wird abgewaehlt, "Gruppe C" wird neu angehakt.
        NutzerDto dto = NutzerDto.from(tn);
        dto.gruppenwerteByKategorie = Map.of("Klasse", List.of("Gruppe A", "Gruppe C"));
        organisatorService.updateUser(tnId, dto, null);

        Teilnehmer updated = Teilnehmer.findById(tnId);
        assertThat(updated.getTeilnehmerGruppenwerte()).containsExactlyInAnyOrder(gruppeA, gruppeC);

        // Alle Haken entfernen muss ebenfalls moeglich sein, nicht nur Hinzufuegen.
        NutzerDto dto2 = NutzerDto.from(Teilnehmer.findById(tnId));
        dto2.gruppenwerteByKategorie = Map.of("Klasse", List.of());
        organisatorService.updateUser(tnId, dto2, null);

        Teilnehmer updated2 = Teilnehmer.findById(tnId);
        assertThat(updated2.getTeilnehmerGruppenwerte()).isEmpty();
    }


    @Test
    @Transactional
    public void testCreateUser_BetrachterWithGruppenwerte_Succeeds() {
        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", false, false);
        klasse.persist();
        GruppenkategorieWert gruppeA = new GruppenkategorieWert(klasse, "Gruppe A");
        gruppeA.persist();
        GruppenkategorieWert gruppeB = new GruppenkategorieWert(klasse, "Gruppe B");
        gruppeB.persist();

        NutzerDto dto = new NutzerDto("BETRACHTER", "betrachter@example.com", "Lisa", "Lehrer", true);
        dto.loginName = "lisa.lehrer";
        // "Klasse" ist NICHT mehrwertig - fuer einen Teilnehmer wuerde nur einer der beiden Werte
        // uebernommen; ein Betrachter darf beide gleichzeitig sehen (siehe #718).
        dto.gruppenwerteByKategorie = Map.of("Klasse", List.of("Gruppe A", "Gruppe B"));

        NutzerDto created = organisatorService.createUser(dto, List.of(veranstaltung.getId()), null);

        Betrachter persisted = Betrachter.findById(created.id);
        assertThat(persisted.getBetrachterGruppenwerte()).containsExactlyInAnyOrder(gruppeA, gruppeB);
        assertThat(created.gruppenwerteByKategorie.get("Klasse")).containsExactlyInAnyOrder("Gruppe A", "Gruppe B");
    }


    @Test
    @Transactional
    public void testUpdateUser_BetrachterGruppenwerteByKategorie_ReplacesExistingSet() {
        Gruppenkategorie klasse = new Gruppenkategorie(veranstaltung, "Klasse", false, false);
        klasse.persist();
        GruppenkategorieWert gruppeA = new GruppenkategorieWert(klasse, "Gruppe A");
        gruppeA.persist();
        GruppenkategorieWert gruppeB = new GruppenkategorieWert(klasse, "Gruppe B");
        gruppeB.persist();

        Betrachter betrachter = new Betrachter();
        betrachter.assignLoginName("betrachterexample");
        betrachter.setEmail("betrachter@example.com");
        betrachter.setFirstName("Lisa");
        betrachter.setLastName("Lehrer");
        betrachter.persist();
        // veranstaltung stammt aus der @BeforeEach-Transaktion und ist in dieser Test-Transaktion
        // detached - Nutzer.veranstaltungen cascade-persisted (PERSIST) beim addVeranstaltung(...)
        // sonst ein detached Entity.
        betrachter.addVeranstaltung(Veranstaltung.findById(veranstaltung.getId()));
        betrachter.addBetrachterGruppenwert(gruppeA);
        Long betrachterId = betrachter.getId();

        NutzerDto dto = NutzerDto.from(Betrachter.findById(betrachterId));
        dto.gruppenwerteByKategorie = Map.of("Klasse", List.of("Gruppe B"));
        organisatorService.updateUser(betrachterId, dto, null);

        Betrachter updated = Betrachter.findById(betrachterId);
        assertThat(updated.getBetrachterGruppenwerte()).containsExactly(gruppeB);

        NutzerDto dto2 = NutzerDto.from(Betrachter.findById(betrachterId));
        dto2.gruppenwerteByKategorie = Map.of("Klasse", List.of());
        organisatorService.updateUser(betrachterId, dto2, null);

        Betrachter updated2 = Betrachter.findById(betrachterId);
        assertThat(updated2.getBetrachterGruppenwerte()).isEmpty();
    }


    @Test
    @Transactional
    public void importOrganisatorenFromCsv_ueberspringtWennEmailBereitsUnterAnderemLoginNameExistiert() throws Exception {
        // Simuliert einen fruehreren, nur teilweise geglueckten Import: derselbe Mensch existiert
        // schon lokal, aber unter einem anderen loginName als im aktuellen CSV.
        Organisator bestehender = new Organisator();
        bestehender.assignLoginName("k.jessen");
        bestehender.setEmail("kathrin.jessen@rks-linz.de");
        bestehender.setFirstName("Kathrin");
        bestehender.setLastName("Jessen");
        bestehender.persist();

        Path csv = Files.createTempFile("organisatoren", ".csv");
        Files.writeString(csv, "Vorname;Nachname;LoginName;Email\nKathrin;Jessen;kathrin.jessen;kathrin.jessen@rks-linz.de\n");

        int anzahl = organisatorService.importOrganisatorenFromCsv(csv);

        assertThat(anzahl).isEqualTo(0);
        assertThat(Nutzer.findByLoginName("kathrin.jessen")).isNull();
        verify(keycloakUserProvisioningService, never()).createUser(any());

        Files.deleteIfExists(csv);
    }


    private Administrator persistedAdministrator(String loginName) {
        Administrator administrator = new Administrator();
        administrator.assignLoginName(loginName);
        administrator.setEmail(loginName + "@example.com");
        administrator.persist();
        return administrator;
    }


    @Test
    @Transactional
    public void changeRole_organisatorZuAdministrator_durchAdministrator_succeeds() {
        Administrator caller = persistedAdministrator("aufsteigender.admin");

        NutzerDto updated = organisatorService.changeRole(testUserId, "ADMINISTRATOR", caller.getLoginName());

        assertThat(updated.role).isEqualTo("ADMINISTRATOR");
        assertThat(Nutzer.<Nutzer>findById(testUserId)).isInstanceOf(Administrator.class);
    }


    // Regression: nur ein Administrator darf einen Nutzer zum Administrator hochstufen - sonst
    // koennte sich ein einfacher Organisator selbst zum Administrator machen.
    @Test
    @Transactional
    public void changeRole_organisatorZuAdministrator_durchOrganisator_wirdAbgelehnt() {
        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> organisatorService.changeRole(testUserId, "ADMINISTRATOR", "testexample"))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(403));

        assertThat(Nutzer.<Nutzer>findById(testUserId)).isNotInstanceOf(Administrator.class);
    }


    @Test
    @Transactional
    public void changeRole_letzterAdministratorHerabstufen_wirdAbgelehnt() {
        Administrator admin = persistedAdministrator("einziger.admin");

        assertThatExceptionOfType(UpdateNutzerException.class)
            .isThrownBy(() -> organisatorService.changeRole(admin.getId(), "ORGANISATOR", null));

        assertThat(Nutzer.<Nutzer>findById(admin.getId())).isInstanceOf(Administrator.class);
    }


    @Test
    @Transactional
    public void changeRole_administratorHerabstufenMitZweitemAdmin_succeeds() {
        Administrator admin1 = persistedAdministrator("admin.eins");
        persistedAdministrator("admin.zwei");

        NutzerDto updated = organisatorService.changeRole(admin1.getId(), "ORGANISATOR", null);

        assertThat(updated.role).isEqualTo("ORGANISATOR");
        Nutzer reloaded = Nutzer.findById(admin1.getId());
        assertThat(reloaded).isNotInstanceOf(Administrator.class);
        assertThat(reloaded).isInstanceOf(Organisator.class);
    }


    // Regression: analog zu changeRole - nur ein Administrator darf einen neuen Administrator anlegen.
    @Test
    @Transactional
    public void createUser_administratorDurchOrganisator_wirdAbgelehnt() {
        NutzerDto dto = new NutzerDto("ADMINISTRATOR", "neuer.admin@example.com", "Neuer", "Admin", true);
        dto.loginName = "neuer.admin";

        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> organisatorService.createUser(dto, null, "testexample"))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(403));

        assertThat(Nutzer.findByLoginName("neuer.admin")).isNull();
    }


    @Test
    @Transactional
    public void createUser_administratorDurchAdministrator_succeeds() {
        Administrator caller = persistedAdministrator("bestehender.admin");
        NutzerDto dto = new NutzerDto("ADMINISTRATOR", "neuer.admin2@example.com", "Neuer", "Admin", true);
        dto.loginName = "neuer.admin2";

        NutzerDto created = organisatorService.createUser(dto, null, caller.getLoginName());

        assertThat(Nutzer.<Nutzer>findById(created.id)).isInstanceOf(Administrator.class);
    }


    @Test
    @Transactional
    public void deleteUser_letzterAdministrator_wirdAbgelehnt() {
        Administrator admin = persistedAdministrator("letzter.admin");

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> organisatorService.deleteUser(admin.getId()));

        assertThat(Nutzer.<Nutzer>findById(admin.getId())).isNotNull();
    }


    @Test
    @Transactional
    public void deleteUser_administratorMitZweitemAdmin_succeeds() {
        Administrator admin1 = persistedAdministrator("admin.a");
        persistedAdministrator("admin.b");

        boolean deleted = organisatorService.deleteUser(admin1.getId());

        assertThat(deleted).isTrue();
        assertThat(Nutzer.<Nutzer>findById(admin1.getId())).isNull();
    }


    /**
     * Regressionstest: das Protokoll-Ereignis beim Löschen eines Nutzers nutzte bisher dessen
     * E-Mail-Adresse - die ist aber nicht pflicht (kein nullable=false auf Nutzer.email) und kann
     * z.B. bei über CSV importierten Nutzern ohne Adresse fehlen. Der loginName ist dagegen immer
     * gesetzt und identifiziert den Nutzer eindeutig.
     */
    @Test
    @Transactional
    public void deleteUser_mitNullEmail_protokolliertLoginNameStattEmail() {
        Administrator admin1 = persistedAdministrator("admin.ohne.email");
        admin1.setEmail(null);
        persistedAdministrator("admin.mit.email");

        boolean deleted = organisatorService.deleteUser(admin1.getId());

        assertThat(deleted).isTrue();
        Protokoll eintrag = Protokoll.<Protokoll>find(
                "kategorie = ?1 and ereignis = ?2 order by id desc", ProtokollKategorie.NUTZER, "Nutzer gelöscht")
            .firstResult();
        assertThat(eintrag).isNotNull();
        assertThat(eintrag.getDetails()).contains("admin.ohne.email").doesNotContain("null");
    }


    @Test
    @Transactional
    public void deleteUser_einzigerOrganisatorEinerVeranstaltung_wirdAbgelehnt() {
        Organisator organisator = Nutzer.findById(testUserId);
        Veranstaltung v = Veranstaltung.findById(veranstaltung.getId());
        organisator.addVeranstaltung(v);
        v.persistAndFlush();

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> organisatorService.deleteUser(testUserId));

        assertThat(Nutzer.<Nutzer>findById(testUserId)).isNotNull();
    }


    @Test
    @Transactional
    public void deleteUser_organisatorMitZweitemOrganisatorDerVeranstaltung_succeeds() {
        Organisator organisator = Nutzer.findById(testUserId);
        Veranstaltung v = Veranstaltung.findById(veranstaltung.getId());
        organisator.addVeranstaltung(v);
        Organisator zweiterOrganisator = new Organisator();
        zweiterOrganisator.assignLoginName("zweiter.organisator");
        zweiterOrganisator.setEmail("zweiter.organisator@example.com");
        zweiterOrganisator.persist();
        zweiterOrganisator.addVeranstaltung(v);
        v.persistAndFlush();

        boolean deleted = organisatorService.deleteUser(testUserId);

        assertThat(deleted).isTrue();
        assertThat(Nutzer.<Nutzer>findById(testUserId)).isNull();
    }


    @Test
    @Transactional
    public void updateUser_entferntEinzigenOrganisatorAusVeranstaltung_wirdAbgelehnt() {
        Organisator organisator = Nutzer.findById(testUserId);
        Veranstaltung v = Veranstaltung.findById(veranstaltung.getId());
        organisator.addVeranstaltung(v);
        v.persistAndFlush();

        NutzerDto dto = NutzerDto.from(organisator);

        assertThatExceptionOfType(UpdateNutzerException.class)
            .isThrownBy(() -> organisatorService.updateUser(testUserId, dto, List.of()));

        assertThat(Nutzer.<Organisator>findById(testUserId).getVeranstaltungen())
            .extracting(IdEntity::getId)
            .contains(v.getId());
    }


    @Test
    @Transactional
    public void deleteVortrag_wahlvortragMitPositivenPrioritaeten_benachrichtigtOrganisatorenUndBetroffeneTeilnehmer() {
        Organisator organisator = Nutzer.findById(testUserId);
        Veranstaltung v = Veranstaltung.findById(veranstaltung.getId());
        organisator.addVeranstaltung(v);
        v.persistAndFlush();

        Referent referent = new Referent();
        referent.assignLoginName("referent.fuer.loeschtest");
        referent.setEmail("referent.fuer.loeschtest@example.com");
        referent.persist();

        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel("Zu löschender Vortrag");
        wv.setReferent(referent);
        wv.setVeranstaltung(v);
        wv.persist();

        new Prioritaet(Teilnehmer.findById(tnId), wv, 6).persist();

        boolean deleted = organisatorService.deleteVortrag(wv.getId(), v);

        assertThat(deleted).isTrue();
        assertThat(Nachricht.findFuerEmpfaenger(organisator)).hasSize(1);
        assertThat(Nachricht.findFuerEmpfaenger(Teilnehmer.findById(tnId))).hasSize(1);
    }

    // -------------------------------------------------------------------
    // Zusatzrollen (siehe #751)
    // -------------------------------------------------------------------


    private Referent persistedReferent(String loginName) {
        Referent referent = new Referent();
        referent.assignLoginName(loginName);
        referent.setEmail(loginName + "@example.com");
        referent.persist();
        return referent;
    }


    private Betrachter persistedBetrachter(String loginName) {
        Betrachter betrachter = new Betrachter();
        betrachter.assignLoginName(loginName);
        betrachter.setEmail(loginName + "@example.com");
        betrachter.persist();
        return betrachter;
    }


    @Test
    @Transactional
    public void grantZusatzrolle_administratorAnSichSelbst_erlaubtAlleDrei() {
        Administrator admin = persistedAdministrator("admin.sich.selbst");

        for (String role : List.of("TEILNEHMER", "REFERENT", "BETRACHTER")) {
            NutzerDto updated = organisatorService.grantZusatzrolle(admin.getId(), role, admin.getLoginName());
            assertThat(updated.zusatzRollen).contains(role);
        }
        assertThat(Nutzer.<Nutzer>findById(admin.getId()).hatRolle("TEILNEHMER")).isTrue();
        assertThat(Nutzer.<Nutzer>findById(admin.getId()).hatRolle("REFERENT")).isTrue();
        assertThat(Nutzer.<Nutzer>findById(admin.getId()).hatRolle("BETRACHTER")).isTrue();
        verify(keycloakUserProvisioningService).grantRealmRole(any(), eq("TEILNEHMER"));
        verify(keycloakUserProvisioningService).grantRealmRole(any(), eq("REFERENT"));
        verify(keycloakUserProvisioningService).grantRealmRole(any(), eq("BETRACHTER"));
    }


    @Test
    @Transactional
    public void grantZusatzrolle_administratorAnOrganisator_erlaubt() {
        Administrator admin = persistedAdministrator("admin.vergibt");
        Organisator organisator = Nutzer.findById(testUserId);

        organisatorService.grantZusatzrolle(organisator.getId(), "REFERENT", admin.getLoginName());

        assertThat(Nutzer.<Nutzer>findById(testUserId).hatRolle("REFERENT")).isTrue();
    }


    @Test
    @Transactional
    public void grantZusatzrolle_administratorAnAnderenAdministrator_erlaubt() {
        Administrator admin = persistedAdministrator("admin.vergibt.2");
        Administrator zielAdmin = persistedAdministrator("admin.ziel");

        organisatorService.grantZusatzrolle(zielAdmin.getId(), "BETRACHTER", admin.getLoginName());

        assertThat(Nutzer.<Nutzer>findById(zielAdmin.getId()).hatRolle("BETRACHTER")).isTrue();
    }


    @Test
    @Transactional
    public void grantZusatzrolle_administratorAnTeilnehmer_wirdAbgelehnt() {
        Administrator admin = persistedAdministrator("admin.verweigert");

        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> organisatorService.grantZusatzrolle(tnId, "REFERENT", admin.getLoginName()))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(403));

        assertThat(Nutzer.<Nutzer>findById(tnId).hatRolle("REFERENT")).isFalse();
    }


    @Test
    @Transactional
    public void grantZusatzrolle_organisatorAnSichSelbst_referentErlaubt() {
        Organisator organisator = Nutzer.findById(testUserId);

        organisatorService.grantZusatzrolle(testUserId, "REFERENT", organisator.getLoginName());

        assertThat(Nutzer.<Nutzer>findById(testUserId).hatRolle("REFERENT")).isTrue();
    }


    @Test
    @Transactional
    public void grantZusatzrolle_organisatorAnTeilnehmer_referentErlaubt() {
        Organisator organisator = Nutzer.findById(testUserId);

        organisatorService.grantZusatzrolle(tnId, "REFERENT", organisator.getLoginName());

        assertThat(Nutzer.<Nutzer>findById(tnId).hatRolle("REFERENT")).isTrue();
    }


    @Test
    @Transactional
    public void grantZusatzrolle_organisatorAnTeilnehmer_teilnehmerRolleWirdAbgelehnt() {
        Organisator organisator = Nutzer.findById(testUserId);

        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> organisatorService.grantZusatzrolle(tnId, "BETRACHTER", organisator.getLoginName()))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(403));
    }


    @Test
    @Transactional
    public void grantZusatzrolle_organisatorAnAnderenOrganisator_wirdAbgelehnt() {
        Organisator organisator = Nutzer.findById(testUserId);
        Organisator zielOrganisator = new Organisator();
        zielOrganisator.assignLoginName("ziel.organisator");
        zielOrganisator.setEmail("ziel.organisator@example.com");
        zielOrganisator.persist();

        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> organisatorService.grantZusatzrolle(zielOrganisator.getId(), "REFERENT", organisator.getLoginName()))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(403));
    }


    @Test
    @Transactional
    public void grantZusatzrolle_ungueltigeRolle_wirdAbgelehnt() {
        Administrator admin = persistedAdministrator("admin.ungueltig");

        assertThatExceptionOfType(WebApplicationException.class)
            .isThrownBy(() -> organisatorService.grantZusatzrolle(testUserId, "ORGANISATOR", admin.getLoginName()))
            .satisfies(e -> assertThat(e.getResponse().getStatus()).isEqualTo(400));
    }


    @Test
    @Transactional
    public void grantZusatzrolle_bereitsGehalteneRolle_istNoOp() {
        Administrator admin = persistedAdministrator("admin.noop");
        organisatorService.grantZusatzrolle(admin.getId(), "REFERENT", admin.getLoginName());

        NutzerDto updated = organisatorService.grantZusatzrolle(admin.getId(), "REFERENT", admin.getLoginName());

        assertThat(updated.zusatzRollen).containsExactly("REFERENT");
        verify(keycloakUserProvisioningService, times(1)).grantRealmRole(any(), eq("REFERENT"));
    }


    @Test
    @Transactional
    public void revokeZusatzrolle_referentMitVortrag_wirdAbgelehnt() {
        Organisator organisator = Nutzer.findById(testUserId);
        Veranstaltung v = Veranstaltung.findById(veranstaltung.getId());

        Nutzer teilnehmer = Nutzer.findById(tnId);
        organisatorService.grantZusatzrolle(tnId, "REFERENT", organisator.getLoginName());
        teilnehmer = Nutzer.findById(tnId);

        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel("Vortrag des Zusatz-Referenten");
        wv.setReferent(teilnehmer);
        wv.setVeranstaltung(v);
        wv.persist();

        assertThatExceptionOfType(UpdateNutzerException.class)
            .isThrownBy(() -> organisatorService.revokeZusatzrolle(tnId, "REFERENT", organisator.getLoginName()));

        assertThat(Nutzer.<Nutzer>findById(tnId).hatRolle("REFERENT")).isTrue();
    }


    @Test
    @Transactional
    public void revokeZusatzrolle_referentOhneVortrag_erlaubt() {
        Organisator organisator = Nutzer.findById(testUserId);
        organisatorService.grantZusatzrolle(tnId, "REFERENT", organisator.getLoginName());

        organisatorService.revokeZusatzrolle(tnId, "REFERENT", organisator.getLoginName());

        assertThat(Nutzer.<Nutzer>findById(tnId).hatRolle("REFERENT")).isFalse();
        verify(keycloakUserProvisioningService).revokeRealmRole(any(), eq("REFERENT"));
    }


    @Test
    @Transactional
    public void revokeZusatzrolle_teilnehmerMitPrioritaeten_wirdAbgelehnt() {
        Administrator admin = persistedAdministrator("admin.entzieht");
        Organisator organisator = Nutzer.findById(testUserId);
        Veranstaltung v = Veranstaltung.findById(veranstaltung.getId());
        organisatorService.grantZusatzrolle(organisator.getId(), "TEILNEHMER", admin.getLoginName());
        Nutzer organisatorAlsTeilnehmer = Nutzer.findById(testUserId);

        Referent referent = persistedReferent("referent.fuer.prio.test");
        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel("Wahlvortrag für Prio-Test");
        wv.setReferent(referent);
        wv.setVeranstaltung(v);
        wv.persist();
        new Prioritaet(organisatorAlsTeilnehmer, wv, 5).persist();

        assertThatExceptionOfType(UpdateNutzerException.class)
            .isThrownBy(() -> organisatorService.revokeZusatzrolle(testUserId, "TEILNEHMER", admin.getLoginName()));

        assertThat(Nutzer.<Nutzer>findById(testUserId).hatRolle("TEILNEHMER")).isTrue();
    }


    @Test
    @Transactional
    public void revokeZusatzrolle_betrachterRolle_istImmerErlaubt() {
        Administrator admin = persistedAdministrator("admin.entzieht.betrachter");
        organisatorService.grantZusatzrolle(admin.getId(), "BETRACHTER", admin.getLoginName());

        organisatorService.revokeZusatzrolle(admin.getId(), "BETRACHTER", admin.getLoginName());

        assertThat(Nutzer.<Nutzer>findById(admin.getId()).hatRolle("BETRACHTER")).isFalse();
    }


    @Test
    @Transactional
    public void revokeZusatzrolle_nichtGehalteneRolle_istNoOpUndRuftKeycloakNichtAuf() {
        Administrator admin = persistedAdministrator("admin.revoke.noop");

        organisatorService.revokeZusatzrolle(admin.getId(), "REFERENT", admin.getLoginName());

        verify(keycloakUserProvisioningService, never()).revokeRealmRole(any(), any());
    }


    /**
     * Integrationstest fuer die verbreiterte Relation (siehe #751 Stufe 1): ein Teilnehmer mit
     * der Zusatzrolle REFERENT muss als {@code Vortrag.referent} persistieren koennen, exakt wie
     * ein primaerer Referent - das ist der eigentliche Zweck des Datenmodell-Umbaus aus Commit 1.
     */
    @Test
    @Transactional
    public void grantZusatzrolle_referentAnTeilnehmer_ermoeglichtVortragMitDiesemReferenten() {
        Organisator organisator = Nutzer.findById(testUserId);
        Veranstaltung v = Veranstaltung.findById(veranstaltung.getId());

        organisatorService.grantZusatzrolle(tnId, "REFERENT", organisator.getLoginName());
        Nutzer teilnehmerAlsReferent = Nutzer.findById(tnId);

        Wahlvortrag wv = new Wahlvortrag();
        wv.setTitel("Vortrag des Teilnehmer-Referenten");
        wv.setReferent(teilnehmerAlsReferent);
        wv.setVeranstaltung(v);
        wv.persistAndFlush();

        Wahlvortrag reloaded = Wahlvortrag.findById(wv.getId());
        assertThat(reloaded.getReferent().getId()).isEqualTo(tnId);
        assertThat(reloaded.getReferent()).isInstanceOf(Teilnehmer.class);
    }
}
