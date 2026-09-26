package com.reportingportal.auth;

import java.io.Serializable;
import java.util.Set;

/**
 * The logged-in user, stored in the HTTP session under {@link #SESSION_ATTRIBUTE}.
 *
 * Immutable: roles are read once at login. A role change in the database
 * therefore applies from the user's next login. Serializable because Tomcat
 * may write sessions to disk when it restarts.
 *
 * A class with getters, not a record: JSPs read it as ${currentUser.displayName},
 * and the Expression Language in Tomcat 10.1 (EL 5.0) only finds JavaBean
 * getters such as getDisplayName(). Record accessors are supported from EL 6.0.
 */
public final class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SESSION_ATTRIBUTE = "currentUser";

    /** Also the request-attribute name AuthFilter uses, so JSPs can read it without touching the session. */
    public static final String REQUEST_ATTRIBUTE = "currentUser";

    private final long userId;
    private final String username;
    private final String displayName;
    private final Set<String> roles;

    public AuthenticatedUser(long userId, String username, String displayName, Set<String> roles) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.roles = Set.copyOf(roles);   // defensive copy, and unmodifiable
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
