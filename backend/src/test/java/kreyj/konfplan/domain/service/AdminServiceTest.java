package kreyj.konfplan.domain.service;

import io.quarkus.elytron.security.common.BcryptUtil;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@QuarkusTest
public class AdminServiceTest {

    @Inject
    AdminService adminService;

    @Inject
    TokenInvalidationService tokenInvalidationService;

    private Long testUserId;
    private Veranstaltung veranstaltung;
    private Long tnId;

    @BeforeEach
    @Transactional
    public void setUp() {
        tokenInvalidationService.reset();
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


    @Test
    @Transactional
    public void testConfirmEmailChange_Success() {
        // 1. Initiate email change
        String newEmail = "new.email@example.com";
        String token = UUID.randomUUID().toString();
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.setNewEmail(newEmail);
        testUser.setEmailChangeToken(token);
        testUser.setEmailChangeTokenExpiry(LocalDateTime.now().plusHours(1));

        // 2. Confirm email change
        boolean result = adminService.confirmEmailChange(token);

        // 3. Verify the change
        assertThat(result).isTrue();
        Nutzer updatedUser = Nutzer.findById(testUserId);
        assertThat(updatedUser.getEmail()).isEqualTo(newEmail);
        assertThat(updatedUser.getNewEmail()).isNull();
        assertThat(updatedUser.getEmailChangeToken()).isNull();
        assertThat(updatedUser.getEmailChangeTokenExpiry()).isNull();
    }

    @Test
    public void testConfirmEmailChange_InvalidToken() {
        // 1. Initiate email change
        String newEmail = "new.email@example.com";
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.setNewEmail(newEmail);
        testUser.setEmailChangeToken(UUID.randomUUID().toString());
        testUser.setEmailChangeTokenExpiry(LocalDateTime.now().plusHours(1));

        // 2. Attempt to confirm with an invalid token
        boolean result = adminService.confirmEmailChange("invalid-token");

        // 3. Verify that the change did not happen
        assertThat(result).isFalse();
        Nutzer user = Nutzer.findById(testUserId);
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getNewEmail()).isEqualTo(newEmail); // The pending email should still be there
    }

    @Test
    @Transactional
    public void testConfirmEmailChange_ExpiredToken() {
        // 1. Initiate email change with an expired token
        String newEmail = "new.email@example.com";
        String token = UUID.randomUUID().toString();
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.setNewEmail(newEmail);
        testUser.setEmailChangeToken(token);
        testUser.setEmailChangeTokenExpiry(LocalDateTime.now().minusHours(1)); // Token is already expired

        // 2. Attempt to confirm with the expired token
        boolean result = adminService.confirmEmailChange(token);

        // 3. Verify that the change did not happen and the token fields are cleared
        assertThat(result).isFalse();
        Nutzer user = Nutzer.findById(testUserId);
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getNewEmail()).isNull();
        assertThat(user.getEmailChangeToken()).isNull();
        assertThat(user.getEmailChangeTokenExpiry()).isNull();
    }

    @Test
    @Transactional
    public void testConfirmEmailChange_MultipleConfirmations() {
        // 1. Initiate email change
        String newEmail = "new.email@example.com";
        String token = UUID.randomUUID().toString();
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.setNewEmail(newEmail);
        testUser.setEmailChangeToken(token);
        testUser.setEmailChangeTokenExpiry(LocalDateTime.now().plusHours(1));

        // 2. Confirm email change for the first time
        boolean firstResult = adminService.confirmEmailChange(token);
        assertThat(firstResult).isTrue();

        // 3. Attempt to confirm the change again with the same token
        boolean secondResult = adminService.confirmEmailChange(token);
        assertThat(secondResult).isFalse();

        // 4. Verify that the email remains the new email
        Nutzer updatedUser = Nutzer.findById(testUserId);
        assertThat(updatedUser.getEmail()).isEqualTo(newEmail);
    }


    // Regression: ein Admin-Konto ohne E-Mail-Adresse kann sich bei vergessenem Passwort nicht
    // selbst wiederherstellen (AuthResource#forgotPassword) und es gab bislang auch keinen
    // anderen Weg (siehe testResetPassword_* unten für den neuen Rettungsweg).
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
        assertThat(BcryptUtil.matches("einNeuesPasswort123", updated.getPasswordHash())).isTrue();
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
    public void testResetPassword_InvalidatesTokensIssuedBeforeTheReset() {
        // Ein von einem Admin ausgeloester Passwort-Reset (Rettungsweg fuer Konten ohne
        // funktionierende E-Mail) muss ein bereits gestohlenes/kompromittiertes Token ebenso
        // ungueltig machen wie der Self-Service-Reset (siehe TokenInvalidationService).
        Instant beforeReset = Instant.now().minusSeconds(5);
        assertThat(tokenInvalidationService.isValid("testexample", beforeReset)).isTrue();

        adminService.resetPassword(testUserId, "einNeuesPasswort123");

        assertThat(tokenInvalidationService.isValid("testexample", beforeReset)).isFalse();
        assertThat(tokenInvalidationService.isValid("testexample", Instant.now().plusSeconds(5))).isTrue();
    }
}
