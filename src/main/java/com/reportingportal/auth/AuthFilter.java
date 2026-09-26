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

/**
 * Second filter: nothing except the login page, the health check and static
 * files can be reached without a logged-in session.
 *
 * "Logged in" means the session holds an AuthenticatedUser, which only
 * LoginServlet puts there after checking the password. This filter only checks
 * *authentication* (who you are); which reports you may see (*authorization*)
 * is checked by each servlet with AccessPolicy.
 */
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = pathWithinApp(request);
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        // getSession(false): look up the existing session, never create one here.
        HttpSession session = request.getSession(false);
        AuthenticatedUser user = session == null ? null
                : (AuthenticatedUser) session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE);

        if (user == null) {
            refuse(request, response, path);
            return;
        }

        // Expose the user to JSPs through request scope, so pages never need the session.
        request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, user);
        // Pages behind the login contain business data: browsers and proxies must not store them.
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }

    /**
     * The decoded, normalised path after the context path, e.g. "/reports/monthly_revenue".
     * Built from getServletPath + getPathInfo rather than the raw request URI, so tricks
     * like "/login;x=1/../reports" or "%2e%2e" cannot make a protected path look public.
     */
    static String pathWithinApp(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        return request.getServletPath() + (pathInfo == null ? "" : pathInfo);
    }

    static boolean isPublic(String path) {
        return path.equals("/login")
                || path.equals("/health")
                || path.startsWith("/static/");
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        if (path.startsWith("/api/")) {
            // A script calling fetch() needs a status code, not a redirect to an HTML page.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"not_authenticated\"}");
            return;
        }
        // Always back to the login page, with no "return to" URL: that would be an
        // open-redirect risk. After login everyone lands on /reports.
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
