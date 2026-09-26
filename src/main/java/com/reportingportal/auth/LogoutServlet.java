package com.reportingportal.auth;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * POST /logout ends the session.
 *
 * POST only (GET gets 405 from HttpServlet): a logout link reachable by GET
 * could be triggered from any other site with an <img> tag. As a POST it
 * needs the CSRF token like every other state change.
 */
@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LogoutServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object user = session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE);
            if (user instanceof AuthenticatedUser authenticated) {
                LOG.info("Logout: user=" + authenticated.getUsername());
            }
            // Removes every attribute and makes the session id useless, on the server side.
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/login?loggedOut");
    }
}
