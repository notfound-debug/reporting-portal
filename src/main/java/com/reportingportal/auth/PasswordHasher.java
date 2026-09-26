package com.reportingportal.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * BCrypt password hashing.
 *
 * Why BCrypt and not SHA-256: a plain hash is fast, so a stolen table can be
 * attacked with billions of guesses per second. BCrypt is deliberately slow
 * (cost 12 = 2^12 rounds, about a quarter of a second), and it stores a random
 * salt inside the 60-character hash, so two users with the same password get
 * different hashes and precomputed tables are useless.
 */
public final class PasswordHasher {

    /** Each +1 doubles the work. 12 is about 0.25 s per check on a normal CPU. */
    static final int COST = 12;

    /** BCrypt only looks at the first 72 bytes; longer passwords are rejected, not silently cut. */
    public static final int MAX_PASSWORD_BYTES = 72;

    /**
     * A hash of a random value generated once when this class loads, so nobody
     * knows it. Checking a password against it when the username does not exist
     * makes a failed login take the same time either way, so response times do
     * not reveal which usernames exist.
     */
    private static final String DUMMY_HASH = BCrypt.withDefaults()
            .hashToString(COST, UUID.randomUUID().toString().toCharArray());

    private PasswordHasher() {
    }

    public static boolean isAcceptableLength(String password) {
        return password != null && !password.isEmpty()
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    public static String hash(String password) {
        requireAcceptableLength(password);
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray());
    }

    public static boolean matches(String password, String storedHash) {
        requireAcceptableLength(password);
        return BCrypt.verifyer().verify(password.toCharArray(), storedHash).verified;
    }

    /** Spends the same time as a real check, for logins with an unknown username. */
    public static void matchAgainstDummy(String password) {
        matches(password, DUMMY_HASH);
    }

    private static void requireAcceptableLength(String password) {
        if (!isAcceptableLength(password)) {
            throw new IllegalArgumentException("Password must be 1 to " + MAX_PASSWORD_BYTES + " bytes");
        }
    }
}
