package com.reportingportal.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * CSRF protection with the synchronizer-token pattern.
 *
 * The attack: while a user is logged in, another website makes their browser
 * submit a form to the portal (for example, create an export schedule). The
 * browser attaches the session cookie automatically, so the request looks genuine.
 *
 * The defence: every session gets a random token that only the portal's own
 * pages know. Every POST form carries it in a hidden field, and CsrfFilter
 * rejects any POST whose token does not match the session's. Another site
 * cannot read the portal's pages, so it cannot learn the token.
 */
public final class CsrfTokens {

    /** Session attribute holding the token, and request attribute exposing it to JSPs. */
    public static final String ATTRIBUTE = "csrfToken";

    /** Name of the hidden form field. */
    public static final String PARAMETER = "_csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfTokens() {
    }

    /** 32 random bytes (256 bits), URL-safe Base64. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Gives the session a fresh token (at session creation and again after login). */
    public static String assignNewToken(HttpSession session) {
        String token = newToken();
        session.setAttribute(ATTRIBUTE, token);
        return token;
    }

    /** Copies the session's token into request scope, where the csrf tag reads it. */
    public static void exposeToView(HttpServletRequest request, HttpSession session) {
        if (session != null) {
            request.setAttribute(ATTRIBUTE, session.getAttribute(ATTRIBUTE));
        }
    }

    /**
     * True only if the session exists, has a token, and the submitted value is equal.
     * MessageDigest.isEqual takes the same time however many leading characters
     * match, so the token cannot be guessed one character at a time by timing.
     */
    public static boolean isValid(HttpSession session, String submitted) {
        if (session == null || submitted == null) {
            return false;
        }
        Object expected = session.getAttribute(ATTRIBUTE);
        if (!(expected instanceof String expectedToken)) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }
}
