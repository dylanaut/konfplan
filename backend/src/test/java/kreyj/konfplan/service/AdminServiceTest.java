package kreyj.konfplan.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import kreyj.konfplan.persistence.Admin;
import kreyj.konfplan.persistence.Nutzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class AdminServiceTest {

    @Inject
    AdminService adminService;

    private Long testUserId;

    @BeforeEach
    @Transactional
    public void setUp() {
        // Clean up existing users to avoid conflicts
        Nutzer.deleteAll();

        // Create a test user
        Nutzer user = new Admin();
        user.email = "test@example.com";
        user.firstName = "Test";
        user.lastName = "User";
        user.persist();
        testUserId = user.id;
    }

    @Test
    @Transactional
    public void testConfirmEmailChange_Success() {
        // 1. Initiate email change
        String newEmail = "new.email@example.com";
        String token = UUID.randomUUID().toString();
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.newEmail = newEmail;
        testUser.emailChangeToken = token;
        testUser.emailChangeTokenExpiry = LocalDateTime.now().plusHours(1);

        // 2. Confirm email change
        boolean result = adminService.confirmEmailChange(token);

        // 3. Verify the change
        assertTrue(result);
        Nutzer updatedUser = Nutzer.findById(testUserId);
        assertEquals(newEmail, updatedUser.email);
        assertNull(updatedUser.newEmail);
        assertNull(updatedUser.emailChangeToken);
        assertNull(updatedUser.emailChangeTokenExpiry);
    }

    @Test
    @Transactional
    public void testConfirmEmailChange_InvalidToken() {
        // 1. Initiate email change
        String newEmail = "new.email@example.com";
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.newEmail = newEmail;
        testUser.emailChangeToken = UUID.randomUUID().toString();
        testUser.emailChangeTokenExpiry = LocalDateTime.now().plusHours(1);

        // 2. Attempt to confirm with an invalid token
        boolean result = adminService.confirmEmailChange("invalid-token");

        // 3. Verify that the change did not happen
        assertFalse(result);
        Nutzer user = Nutzer.findById(testUserId);
        assertEquals("test@example.com", user.email);
        assertEquals(newEmail, user.newEmail); // The pending email should still be there
    }

    @Test
    @Transactional
    public void testConfirmEmailChange_ExpiredToken() {
        // 1. Initiate email change with an expired token
        String newEmail = "new.email@example.com";
        String token = UUID.randomUUID().toString();
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.newEmail = newEmail;
        testUser.emailChangeToken = token;
        testUser.emailChangeTokenExpiry = LocalDateTime.now().minusHours(1); // Token is already expired

        // 2. Attempt to confirm with the expired token
        boolean result = adminService.confirmEmailChange(token);

        // 3. Verify that the change did not happen and the token fields are cleared
        assertFalse(result);
        Nutzer user = Nutzer.findById(testUserId);
        assertEquals("test@example.com", user.email);
        assertNull(user.newEmail);
        assertNull(user.emailChangeToken);
        assertNull(user.emailChangeTokenExpiry);
    }

    @Test
    @Transactional
    public void testConfirmEmailChange_MultipleConfirmations() {
        // 1. Initiate email change
        String newEmail = "new.email@example.com";
        String token = UUID.randomUUID().toString();
        Nutzer testUser = Nutzer.findById(testUserId);
        testUser.newEmail = newEmail;
        testUser.emailChangeToken = token;
        testUser.emailChangeTokenExpiry = LocalDateTime.now().plusHours(1);

        // 2. Confirm email change for the first time
        boolean firstResult = adminService.confirmEmailChange(token);
        assertTrue(firstResult);

        // 3. Attempt to confirm the change again with the same token
        boolean secondResult = adminService.confirmEmailChange(token);
        assertFalse(secondResult);

        // 4. Verify that the email remains the new email
        Nutzer updatedUser = Nutzer.findById(testUserId);
        assertEquals(newEmail, updatedUser.email);
    }
}