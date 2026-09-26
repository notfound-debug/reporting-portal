package com.reportingportal.report;

import com.reportingportal.auth.AuthenticatedUser;
import com.reportingportal.config.AppContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * GET /charts/{code}: the chart page for one of the three charted reports.
 *
 * It renders the report's form (filled in from the URL) and an empty canvas;
 * it runs no query itself. static/js/charts.js then asks /api/reports/{code}
 * for the data with the same parameters, which checks access and validates
 * them again on the server.
 */
@WebServlet("/charts/*")
public class ChartPageServlet extends HttpServlet {

    private ReportRegistry registry;
    private List<String> categories;

    @Override
    @SuppressWarnings("unchecked")
    public void init() {
        registry = (ReportRegistry) getServletContext().getAttribute(AppContextListener.REGISTRY);
        categories = (List<String>) getServletContext().getAttribute(AppContextListener.CATEGORIES);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ReportDefinition report = ReportRequests.findReport(registry, request.getPathInfo());
        if (report == null || !report.isHasChart()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        AuthenticatedUser user = ReportRequests.currentUser(request);
        if (!AccessPolicy.canView(user, report)) {
            ReportRequests.logDenied(user, report, "chart page");
            request.setAttribute("errorMessage", "You do not have access to this report.");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Only to refill the form with what is in the URL; the JSON endpoint reports any errors.
        ValidationResult validation = ParamValidator.validate(report, request.getParameterMap(), categories);
        request.setAttribute("report", report);
        request.setAttribute("submitted", validation.getSubmitted());
        request.setAttribute("errors", Map.of());
        request.setAttribute("states", BrazilStates.CODES);
        request.setAttribute("categories", categories);
        request.getRequestDispatcher("/WEB-INF/jsp/chart.jsp").forward(request, response);
    }
}
