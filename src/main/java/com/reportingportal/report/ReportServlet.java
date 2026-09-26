package com.reportingportal.report;

import com.reportingportal.auth.AuthenticatedUser;
import com.reportingportal.config.AppContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * GET /reports/{code}: a report's form and one page of its results.
 *
 * The order of the checks matters:
 *   1. the report exists             otherwise 404
 *   2. the user may see it           otherwise 403, before any parameter is read
 *   3. the parameters are valid      otherwise 400, the form with messages, no SQL run
 *   4. build the SQL from constants plus bind values, run it, forward to the JSP.
 * GET only: running a report reads data and changes nothing.
 */
@WebServlet("/reports/*")
public class ReportServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ReportServlet.class.getName());

    private static final String VIEW = "/WEB-INF/jsp/report.jsp";

    private ReportRegistry registry;
    private List<String> categories;
    private ReportDao reportDao;

    @Override
    @SuppressWarnings("unchecked")
    public void init() {
        registry = (ReportRegistry) getServletContext().getAttribute(AppContextListener.REGISTRY);
        categories = (List<String>) getServletContext().getAttribute(AppContextListener.CATEGORIES);
        reportDao = new ReportDao((DataSource) getServletContext().getAttribute(AppContextListener.WAREHOUSE_DATA_SOURCE));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // 1. Which report? "/reports/monthly_revenue" gives path info "/monthly_revenue".
        ReportDefinition report = findReport(registry, request.getPathInfo());
        if (report == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 2. May this user see it? Checked on the server for every request,
        //    whatever the menu showed.
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
        if (!AccessPolicy.canView(user, report)) {
            refuse(request, response, user, report);
            return;
        }

        // 3. Validate the form parameters.
        ValidationResult validation = ParamValidator.validate(report, request.getParameterMap(), categories);
        request.setAttribute("report", report);
        request.setAttribute("submitted", validation.getSubmitted());
        request.setAttribute("errors", validation.getErrors());
        request.setAttribute("states", BrazilStates.CODES);
        request.setAttribute("categories", categories);
        if (!validation.isValid()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.getRequestDispatcher(VIEW).forward(request, response);
            return;
        }

        // 4. Run it.
        ValidatedParams params = validation.getParams();
        ReportResult result;
        try {
            result = reportDao.run(report, ReportQuery.forPage(report, params), ReportQuery.PAGE_SIZE);
        } catch (SQLException e) {
            throw new ServletException("Report " + report.getCode() + " failed", e);
        }
        request.setAttribute("result", result);
        request.setAttribute("page", params.getPage());
        request.setAttribute("firstRowNumber", (params.getPage() - 1) * ReportQuery.PAGE_SIZE + 1);
        request.setAttribute("sortColumn", params.getSortColumn());
        request.setAttribute("direction", params.getDirection());
        request.setAttribute("sortLinks", sortLinks(report, params));
        if (params.getPage() > 1) {
            request.setAttribute("previousLink", params.toQueryStringWith(Map.of(ParamValidator.PAGE, String.valueOf(params.getPage() - 1))));
        }
        if (report.isPaged() && result.isMore()) {
            request.setAttribute("nextLink", params.toQueryStringWith(Map.of(ParamValidator.PAGE, String.valueOf(params.getPage() + 1))));
        }
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /** The report for a path like "/monthly_revenue", or null. Shared with the chart and JSON servlets. */
    static ReportDefinition findReport(ReportRegistry registry, String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        return registry.find(pathInfo.substring(1)).orElse(null);
    }

    /** 403 with a fixed message; the attempt is logged. Shared with the chart and JSON servlets. */
    static void refuse(HttpServletRequest request, HttpServletResponse response,
                       AuthenticatedUser user, ReportDefinition report) throws IOException {
        LOG.warning("Access denied: user=" + user.getUsername() + " report=" + report.getCode());
        request.setAttribute("errorMessage", "You do not have access to this report.");
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    /**
     * For each sortable column header: the query string that sorts by it. Clicking
     * the column already sorted on reverses the direction. Paging restarts at 1.
     */
    private static Map<String, String> sortLinks(ReportDefinition report, ValidatedParams params) {
        Map<String, String> links = new LinkedHashMap<>();
        for (String column : report.getSortableColumns().keySet()) {
            SortDirection direction = column.equals(params.getSortColumn())
                    ? params.getDirection().opposite()
                    : SortDirection.ASC;
            links.put(column, params.toQueryStringWith(Map.of(
                    ParamValidator.SORT, column,
                    ParamValidator.DIRECTION, direction.getParameterValue())));
        }
        return links;
    }
}
