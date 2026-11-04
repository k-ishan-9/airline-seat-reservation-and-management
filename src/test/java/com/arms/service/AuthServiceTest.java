package com.arms.service;

import com.arms.model.Role;
import com.arms.model.User;
import com.arms.support.IntegrationTest;
import com.arms.support.TestDb;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AuthServiceTest extends IntegrationTest {

    @Test
    public void registersAndLogsIn() {
        User created = customer("kishan");
        assertEquals(Role.CUSTOMER, created.role());
        User loggedIn = auth.login("kishan", "password1".toCharArray());
        assertEquals(created.id(), loggedIn.id());
    }

    @Test
    public void storesOnlyAHash() {
        customer("kishan");
        String hash = TestDb.string(db, "SELECT password_hash FROM users WHERE username = ?", "kishan");
        assertTrue(hash.startsWith("pbkdf2_sha256$"));
    }

    @Test
    public void validatesInput() {
        expectValidation(() -> auth.register("ab", "Kishan", "password1".toCharArray()));
        expectValidation(() -> auth.register("kishan", "K", "password1".toCharArray()));
        expectValidation(() -> auth.register("kishan", "Kishan", "short1".toCharArray()));
        expectValidation(() -> auth.register("kishan", "Kishan", "lettersonly".toCharArray()));
    }

    @Test
    public void rejectsDuplicateUsernames() {
        customer("kishan");
        expectValidation(() -> customer("kishan"));
    }

    @Test
    public void wrongPasswordAndUnknownUserGetTheSameMessage() {
        customer("kishan");
        String wrongPassword = loginError("kishan", "wrongpass1");
        String unknownUser = loginError("nobody", "wrongpass1");
        assertEquals("Invalid username or password", wrongPassword);
        assertEquals(wrongPassword, unknownUser);
    }

    @Test
    public void locksTheAccountForFifteenMinutesAfterFiveFailures() {
        customer("kishan");
        for (int i = 0; i < 4; i++) loginError("kishan", "wrongpass1");
        assertTrue(loginError("kishan", "wrongpass1").contains("locked"));
        // Even the right password is refused while locked
        assertTrue(loginError("kishan", "password1").contains("locked"));

        clock.advance(Duration.ofMinutes(16));
        assertEquals("kishan", auth.login("kishan", "password1".toCharArray()).username());
        assertEquals(0, TestDb.count(db, "SELECT failed_attempts FROM users WHERE username = ?", "kishan"));
    }

    @Test
    public void seedsOneAdmin() {
        User admin = adminUser();
        assertTrue(admin.isAdmin());
        auth.ensureAdmin("another-pass1".toCharArray()); // running again changes nothing
        assertEquals(1, TestDb.count(db, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'"));
    }

    private String loginError(String username, String password) {
        try {
            auth.login(username, password.toCharArray());
            fail("login should have failed");
            return null;
        } catch (AuthException e) {
            return e.getMessage();
        }
    }

    private static void expectValidation(Runnable action) {
        try {
            action.run();
            fail("expected a ValidationException");
        } catch (ValidationException expected) {
            // ok
        }
    }
}
