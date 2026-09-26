package com.reportingportal.report;

import com.reportingportal.auth.AuthenticatedUser;
import com.reportingportal.config.AppContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET /reports: the landing page after login, listing only the reports the
 * user's roles allow. This is the menu, not the security: each report page and
 * data endpoint checks access again on the server.
 */
@WebServlet("/reports")
public class ReportListServlet extends HttpServlet {

    private ReportRegistry registry;

    @Override
    public void init() {
        registry = (ReportRegistry) getServletContext().getAttribute(AppContextListener.REGISTRY);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
        request.setAttribute("reports", registry.visibleTo(user));
        request.getRequestDispatcher("/WEB-INF/jsp/reports.jsp").forward(request, response);
    }
}
