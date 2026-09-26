package com.reportingportal.auth;

import com.reportingportal.config.AppContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * GET /login shows the form; POST /login checks the password and logs the user in.
 *
 * This is the only class that creates an HTTP session. The GET creates a
 * short "pre-login" session just to hold the CSRF token for the form; a
 * successful POST throws that session away and starts a new one.
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LoginServlet.class.getName());

    private static final String VIEW = "/WEB-INF/jsp/login.jsp";

    /** Same rule as the CHECK constraint on users.username. */
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");

    /** One message for every failure, so the page never reveals which usernames exist. */
    static final String LOGIN_FAILED = "Invalid username or password.";

    private UserDao userDao;

    @Override
    public void init() {
        DataSource dataSource = (DataSource) getServletContext().getAttribute(AppContextListener.DATA_SOURCE);
        userDao = new UserDao(dataSource);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (currentUser(request) != null) {
            response.sendRedirect(request.getContextPath() + "/reports");
            return;
        }
        HttpSession session = request.getSession(true);
        if (session.getAttribute(CsrfTokens.ATTRIBUTE) == null) {
            CsrfTokens.assignNewToken(session);
        }
        CsrfTokens.exposeToView(request, session);
        if (request.getParameter("loggedOut") != null) {
            request.setAttribute("message", "You have been logged out.");
        }
        showForm(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // CsrfFilter has already checked the token before we get here.
        String username = normalise(request.getParameter("username"));
        String password = request.getParameter("password");

        Optional<UserDao.StoredUser> user;
        try {
            user = authenticate(username, password);
        } catch (SQLException e) {
            throw new ServletException("Login failed: database error", e);
        }

        if (user.isEmpty()) {
            LOG.warning("Failed login for username '" + (USERNAME.matcher(username).matches() ? username : "(invalid)")
                    + "' from " + request.getRemoteAddr());
            request.setAttribute("error", LOGIN_FAILED);
            request.setAttribute("username", username);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            showForm(request, response);
            return;
        }

        try {
            startAuthenticatedSession(request, user.get());
        } catch (SQLException e) {
            throw new ServletException("Login failed: could not load roles", e);
        }
        // Post/Redirect/Get: refreshing the next page does not re-send the password.
        response.sendRedirect(request.getContextPath() + "/reports");
    }

    /** Returns the user only if the username exists, is active, and the password matches. */
    private Optional<UserDao.StoredUser> authenticate(String username, String password) throws SQLException {
        if (!USERNAME.matcher(username).matches() || !PasswordHasher.isAcceptableLength(password)) {
            return Optional.empty();
        }
        Optional<UserDao.StoredUser> user = userDao.findActiveByUsername(username);
        if (user.isEmpty()) {
            // Spend the same ~0.25 s a real check takes, so timing does not reveal
            // that this username does not exist.
            PasswordHasher.matchAgainstDummy(password);
            return Optional.empty();
        }
        return PasswordHasher.matches(password, user.get().passwordHash()) ? user : Optional.empty();
    }

    /**
     * Session-fixation defence: an attacker could plant a session id in the
     * victim's browser before login. By invalidating the pre-login session and
     * creating a new one, the id the attacker knows is worthless once the user logs in.
     */
    private void startAuthenticatedSession(HttpServletRequest request, UserDao.StoredUser stored)
            throws SQLException {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);

        AuthenticatedUser user = new AuthenticatedUser(
                stored.userId(), stored.username(), stored.displayName(), userDao.findRoles(stored.userId()));
        session.setAttribute(AuthenticatedUser.SESSION_ATTRIBUTE, user);
        CsrfTokens.assignNewToken(session);

        userDao.recordLogin(stored.userId(), Instant.now());
        LOG.info("Login: user=" + user.getUsername() + " roles=" + user.getRoles());
    }

    private void showForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // The page holds a CSRF token: do not let the browser cache it.
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    private static AuthenticatedUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (AuthenticatedUser) session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE);
    }

    private static String normalise(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
