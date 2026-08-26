package kreyj.konfplan.domain.service;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.adapter.in.web.dto.NutzerDto;
import kreyj.konfplan.domain.exception.BusinessException;
import kreyj.konfplan.domain.exception.CreateVortragException;
import kreyj.konfplan.domain.exception.UpdateNutzerException;
import kreyj.konfplan.domain.exception.UpdateVortragException;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Neigung;
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
public class AdminServiceTest {

    @Inject
    AdminService adminService;

    @InjectMock
    KeycloakUserProvisioningService keycloakUserProvisioningService;

    private Long testUserId;
    private Veranstaltung veranstaltung;
    private Long tnId;

    @BeforeEach
    @Transactional
    public void setUp() {
        // Clean up existing data to avoid conflicts
        // @ElementCollection-Tabelle auf der Subklasse Teilnehmer wird bei einem Bulk-Delete auf
        // Nutzer nicht automatisch mitgeloescht.
        io.quarkus.hibernate.orm.panache.Panache.getEntityManager().createNativeQuery("delete from teilnehmer_neigungen").executeUpdate();
        Nutzer.deleteAll();
        Veranstaltung.deleteAll();

        // Create a test user
        Nutzer user = new Admin();
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
        adminService.createGruppe(veranstaltung.getId(), "Gruppe A");
        List<String> gruppen = adminService.getGruppen(veranstaltung.getId());
        assertThat(gruppen.contains("Gruppe A")).isTrue();
        assertThat(gruppen.size()).isEqualTo(1);
    }

    @Test
    @Transactional
    public void testCreateGruppe_DuplicateName_ThrowsException() {
        adminService.createGruppe(veranstaltung.getId(), "Gruppe A");
        assertThatExceptionOfType(CreateVortragException.class).isThrownBy(() -> adminService.createGruppe(veranstaltung.getId(), "Gruppe A"));
    }

    @Test
    @Transactional
    public void testRenameGruppe() {
        // Setup
        adminService.createGruppe(veranstaltung.getId(), "Gruppe A");
        Teilnehmer tn = Teilnehmer.findById(tnId);
        tn.addGruppe("Gruppe A");
        tn.persist();

        // Rename
        adminService.renameGruppe(veranstaltung.getId(), "Gruppe A", "Gruppe B");

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
        adminService.createGruppe(veranstaltung.getId(), "Gruppe A");
        adminService.createGruppe(veranstaltung.getId(), "Gruppe B");
        assertThatExceptionOfType(UpdateVortragException.class).isThrownBy(() -> adminService.renameGruppe(veranstaltung.getId(), "Gruppe A", "Gruppe B"));
    }

    @Test
    @Transactional
    public void testDeleteGruppe() {
        // Setup
        Teilnehmer tn = Teilnehmer.findById(tnId);
        adminService.createGruppe(veranstaltung.getId(), "Gruppe A");
        tn.addGruppe("Gruppe A");

        // Delete
        adminService.deleteGruppe(veranstaltung.getId(), "Gruppe A");

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

        adminService.createGruppe(veranstaltung.getId(), "Gruppe A");
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


    // Regression: ein Admin-Konto ohne E-Mail-Adresse kann sich bei vergessenem Passwort nicht
    // selbst wiederherstellen (Keycloaks Passwort-Reset braucht eine E-Mail-Adresse) und es gab
    // bislang auch keinen anderen Weg (siehe testResetPassword_* unten für den Rettungsweg).
    @Test
    public void testCreateUser_AdminWithoutEmail_ThrowsException() {
        NutzerDto dto = new NutzerDto("ADMIN", null, "Ohne", "Email", true);
        dto.loginName = "ohne.email";

        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> adminService.createUser(dto, null));

        assertThat(Nutzer.findByLoginName("ohne.email")).isNull();
    }


    @Test
    public void testCreateUser_AdminWithEmail_Succeeds() {
        NutzerDto dto = new NutzerDto("ADMIN", "mit.email@test.de", "Mit", "Email", true);
        dto.loginName = "mit.email";

        NutzerDto created = adminService.createUser(dto, null);

        assertThat(created.email).isEqualTo("mit.email@test.de");
    }


    @Test
    public void testUpdateUser_RemovingAdminEmail_ThrowsException() {
        NutzerDto dto = NutzerDto.from(Nutzer.findById(testUserId));
        dto.email = null;

        assertThatExceptionOfType(UpdateNutzerException.class)
            .isThrownBy(() -> adminService.updateUser(testUserId, dto, null));

        assertThat(Nutzer.<Nutzer>findById(testUserId).getEmail()).isEqualTo("test@example.com");
    }


    @Test
    public void testResetPassword_Success() {
        boolean result = adminService.resetPassword(testUserId, "einNeuesPasswort123");

        assertThat(result).isTrue();
        Nutzer updated = Nutzer.findById(testUserId);
        verify(keycloakUserProvisioningService).resetPassword(eq(updated), eq("einNeuesPasswort123"));
    }


    @Test
    public void testResetPassword_UnknownUser_ReturnsFalse() {
        boolean result = adminService.resetPassword(-1L, "einNeuesPasswort123");

        assertThat(result).isFalse();
    }


    @Test
    public void testResetPassword_PasswordTooShort_ThrowsException() {
        assertThatExceptionOfType(BusinessException.class)
            .isThrownBy(() -> adminService.resetPassword(testUserId, "kurz"));
    }


    @Test
    public void testCreateUser_TeilnehmerWithNeigungen_Succeeds() {
        NutzerDto dto = new NutzerDto("TEILNEHMER", "neu@example.com", "Neue", "Person", true);
        dto.loginName = "neue.person";
        dto.neigungen = Set.of(Neigung.SOZIAL, Neigung.WISSENSCHAFTLICH);

        NutzerDto created = adminService.createUser(dto, null);

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
        adminService.updateUser(tnId, dto, null);

        Teilnehmer updated = Teilnehmer.findById(tnId);
        assertThat(updated.getNeigungen()).containsExactlyInAnyOrder(Neigung.KREATIV, Neigung.MEDIZINISCH);

        // Ein zweites Update mit anderer Auswahl muss die vorherige Auswahl vollstaendig ersetzen
        // (Checkbox-UI: ein Entfernen einzelner Werte muss moeglich sein).
        NutzerDto dto2 = NutzerDto.from(Teilnehmer.findById(tnId));
        dto2.neigungen = Set.of(Neigung.TECHNISCH);
        adminService.updateUser(tnId, dto2, null);

        Teilnehmer updated2 = Teilnehmer.findById(tnId);
        assertThat(updated2.getNeigungen()).containsExactly(Neigung.TECHNISCH);
    }


    @Test
    @Transactional
    public void testUpdateUser_TeilnehmerGruppen_ReplacesExistingSet() {
        Teilnehmer tn = Teilnehmer.findById(tnId);
        tn.addGruppe("Gruppe A");
        tn.addGruppe("Gruppe B");

        // Bearbeiten-Dialog: "Gruppe B" wird abgewaehlt, "Gruppe C" wird neu angehakt.
        NutzerDto dto = NutzerDto.from(tn);
        dto.gruppen = List.of("Gruppe A", "Gruppe C");
        adminService.updateUser(tnId, dto, null);

        Teilnehmer updated = Teilnehmer.findById(tnId);
        assertThat(updated.getGruppen()).containsExactlyInAnyOrder("Gruppe A", "Gruppe C");

        // Alle Haken entfernen muss ebenfalls moeglich sein, nicht nur Hinzufuegen.
        NutzerDto dto2 = NutzerDto.from(Teilnehmer.findById(tnId));
        dto2.gruppen = List.of();
        adminService.updateUser(tnId, dto2, null);

        Teilnehmer updated2 = Teilnehmer.findById(tnId);
        assertThat(updated2.getGruppen()).isEmpty();
    }


    @Test
    @Transactional
    public void importAdminsFromCsv_ueberspringtWennEmailBereitsUnterAnderemLoginNameExistiert() throws Exception {
        // Simuliert einen fruehreren, nur teilweise geglueckten Import: derselbe Mensch existiert
        // schon lokal, aber unter einem anderen loginName als im aktuellen CSV.
        Admin bestehender = new Admin();
        bestehender.assignLoginName("k.jessen");
        bestehender.setEmail("kathrin.jessen@rks-linz.de");
        bestehender.setFirstName("Kathrin");
        bestehender.setLastName("Jessen");
        bestehender.persist();

        Path csv = Files.createTempFile("organisatoren", ".csv");
        Files.writeString(csv, "Vorname;Nachname;LoginName;Email\nKathrin;Jessen;kathrin.jessen;kathrin.jessen@rks-linz.de\n");

        int anzahl = adminService.importAdminsFromCsv(csv);

        assertThat(anzahl).isEqualTo(0);
        assertThat(Nutzer.findByLoginName("kathrin.jessen")).isNull();
        verify(keycloakUserProvisioningService, never()).createUser(any());

        Files.deleteIfExists(csv);
    }
}
