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
import kreyj.konfplan.persistence.Nutzer;
import kreyj.konfplan.persistence.Teilnehmer;
import kreyj.konfplan.persistence.Veranstaltung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
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
}
