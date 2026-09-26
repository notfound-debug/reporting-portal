package com.reportingportal.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Command-line tool: reads one password from standard input and prints its BCrypt hash.
 *
 * Used to create the seed users and to set a new password by hand, since the
 * portal has no user-management screen. Run it with bin/hash-password.sh.
 * The password is read from stdin, not taken as an argument, so it does not
 * appear in the shell history or the process list.
 */
public final class HashPassword {

    private static final int MIN_LENGTH = 10;

    private HashPassword() {
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password = in.readLine();

        if (password == null || password.length() < MIN_LENGTH) {
            System.err.println("Password must be at least " + MIN_LENGTH + " characters.");
            System.exit(1);
        }
        if (!PasswordHasher.isAcceptableLength(password)) {
            System.err.println("Password must be at most " + PasswordHasher.MAX_PASSWORD_BYTES + " bytes.");
            System.exit(1);
        }
        System.out.println(PasswordHasher.hash(password));
    }
}
