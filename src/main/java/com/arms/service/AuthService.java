package com.arms.service;

import com.arms.dao.UserDao;
import com.arms.db.Database;
import com.arms.model.Role;
import com.arms.model.User;
import com.arms.util.PasswordHasher;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Registration and login: validated input, hashed passwords, and lockout after repeated failures. */
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final String INVALID_LOGIN = "Invalid username or password";
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");

    private final Database db;
    private final UserDao users = new UserDao();
    private final PasswordHasher hasher;
    private final Clock clock;
    private final String dummyHash; // compared when the username doesn't exist, so timing is the same

    public AuthService(Database db, PasswordHasher hasher, Clock clock) {
        this.db = db;
        this.hasher = hasher;
        this.clock = clock;
        this.dummyHash = hasher.hash("not-a-real-password".toCharArray());
    }

    public User register(String username, String fullName, char[] password) {
        String name = fullName == null ? "" : fullName.trim();
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ValidationException("Username must be 3-30 letters, digits or underscores");
        }
        if (name.length() < 2 || name.length() > 80) throw new ValidationException("Full name must be 2-80 characters");
        validatePassword(password);

        return createUser(username, name, password, Role.CUSTOMER);
    }

    /** Creates the first admin account at startup if it doesn't exist yet. */
    public void ensureAdmin(char[] password) {
        if (db.read(c -> users.exists(c, "admin"))) return;
        createUser("admin", "System Administrator", password, Role.ADMIN);
    }

    private User createUser(String username, String fullName, char[] password, Role role) {
        String hash = hasher.hash(password);
        return db.inTransaction(c -> {
            if (users.exists(c, username)) throw new ValidationException("That username is already taken");
            try {
                long id = users.insert(c, username, fullName, hash, role);
                return new User(id, username, fullName, role);
            } catch (SQLIntegrityConstraintViolationException e) {
                // Two people registered the same name at the same moment: the UNIQUE key decided
                throw new ValidationException("That username is already taken");
            }
        });
    }

    public User login(String username, char[] password) {
        LocalDateTime now = LocalDateTime.now(clock);
        // The transaction returns an outcome instead of throwing, because a failed attempt
        // must still be COMMITTED (throwing would roll the counter back).
        LoginOutcome outcome = db.inTransaction(c -> {
            UserDao.Credentials creds = users.findForLogin(c, username == null ? "" : username);
            if (creds == null) {
                hasher.verify(password, dummyHash);
                return LoginOutcome.failed(INVALID_LOGIN);
            }
            if (creds.lockedUntil() != null && creds.lockedUntil().isAfter(now)) {
                return LoginOutcome.failed("Account locked after too many failed attempts. Try again after "
                        + creds.lockedUntil().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            if (!hasher.verify(password, creds.passwordHash())) {
                int attempts = creds.failedAttempts() + 1;
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    users.updateLoginState(c, creds.user().id(), 0, now.plus(LOCKOUT));
                    return LoginOutcome.failed("Too many failed attempts. Account locked for 15 minutes.");
                }
                users.updateLoginState(c, creds.user().id(), attempts, null);
                return LoginOutcome.failed(INVALID_LOGIN);
            }
            if (creds.failedAttempts() > 0 || creds.lockedUntil() != null) {
                users.updateLoginState(c, creds.user().id(), 0, null);
            }
            return LoginOutcome.success(creds.user());
        });
        if (outcome.user() == null) throw new AuthException(outcome.error());
        return outcome.user();
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 8 || password.length > 64) {
            throw new ValidationException("Password must be 8-64 characters");
        }
        boolean letter = false;
        boolean digit = false;
        for (char ch : password) {
            letter |= Character.isLetter(ch);
            digit |= Character.isDigit(ch);
        }
        if (!letter || !digit) throw new ValidationException("Password must contain at least one letter and one digit");
    }

    private record LoginOutcome(User user, String error) {
        static LoginOutcome success(User user) {
            return new LoginOutcome(user, null);
        }

        static LoginOutcome failed(String error) {
            return new LoginOutcome(null, error);
        }
    }
}
