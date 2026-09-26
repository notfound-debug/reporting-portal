package com.reportingportal.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Third filter: every state-changing request (POST) must carry the session's
 * CSRF token in the "_csrf" form field. That includes the login form, which
 * stops another site from logging a victim into the attacker's account.
 *
 * GET requests are never checked, which is only safe because of a rule the
 * whole portal follows: GET handlers read data and never change anything.
 */
public class CsrfFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(CsrfFilter.class.getName());

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        HttpSession session = request.getSession(false);

        if (changesState(request.getMethod())
                && !CsrfTokens.isValid(session, request.getParameter(CsrfTokens.PARAMETER))) {
            LOG.warning("CSRF token missing or wrong: " + request.getMethod() + " "
                    + AuthFilter.pathWithinApp(request) + " from " + request.getRemoteAddr());
            request.setAttribute("errorMessage", "This form has expired. Reload the page and try again.");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Let every page's forms print the token (see WEB-INF/tags/csrf.tag).
        CsrfTokens.exposeToView(request, session);
        chain.doFilter(request, response);
    }

    private static boolean changesState(String method) {
        return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));
    }
}
