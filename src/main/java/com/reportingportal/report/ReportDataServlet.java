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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/reports/{code}?<same parameters as the report form>: the report's
 * rows as JSON, used by the chart pages.
 *
 * It applies exactly the same steps, in the same order, as the HTML report page:
 * 404 unknown report, 403 not allowed, 400 invalid parameters, then the same
 * ReportQuery (bound values, whitelisted columns). The only difference is the
 * output format and that it returns all rows up to a cap instead of one page.
 * AuthFilter has already answered 401 if there is no logged-in session.
 */
@WebServlet("/api/reports/*")
public class ReportDataServlet extends HttpServlet {

    /** A chart with more points than this would be unreadable anyway. */
    static final int MAX_ROWS = 1000;

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
        ReportDefinition report = ReportRequests.findReport(registry, request.getPathInfo());
        if (report == null) {
            send(response, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "unknown_report"));
            return;
        }

        AuthenticatedUser user = ReportRequests.currentUser(request);
        if (!AccessPolicy.canView(user, report)) {
            ReportRequests.logDenied(user, report, "api");
            send(response, HttpServletResponse.SC_FORBIDDEN, Map.of("error", "forbidden"));
            return;
        }

        ValidationResult validation = ParamValidator.validate(report, request.getParameterMap(), categories);
        if (!validation.isValid()) {
            send(response, HttpServletResponse.SC_BAD_REQUEST, Map.of("errors", validation.getErrors()));
            return;
        }

        ValidatedParams params = validation.getParams();
        ReportResult result;
        try {
            result = reportDao.run(report, ReportQuery.forAllRows(report, params, MAX_ROWS), MAX_ROWS);
        } catch (SQLException e) {
            throw new ServletException("Report " + report.getCode() + " failed", e);
        }
        send(response, HttpServletResponse.SC_OK, body(report, params, result));
    }

    /** The response body: see "Chart design" in DESIGN.md for the contract. */
    private static Map<String, Object> body(ReportDefinition report, ValidatedParams params, ReportResult result) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (ColumnDef column : result.getColumns()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("name", column.getName());
            json.put("label", column.getLabel());
            json.put("type", column.getType().name());
            columns.add(json);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report", report.getCode());
        body.put("title", report.getTitle());
        body.put("columns", columns);
        body.put("rows", result.getRows());
        body.put("chart", chart(report.getChart(), params));
        body.put("truncated", result.isMore());
        return body;
    }

    private static Map<String, Object> chart(ChartSpec spec, ValidatedParams params) {
        if (spec == null) {
            return null;
        }
        // Series are fixed columns, or the whitelisted column the user picked (e.g. a quarter).
        List<String> series = spec.getSeriesParam() == null
                ? spec.getSeriesColumns()
                : List.of(params.column(spec.getSeriesParam()));
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", spec.getType() == ChartSpec.Type.LINE ? "line" : "bar");
        chart.put("labelColumn", spec.getLabelColumn());
        chart.put("series", series);
        chart.put("stacked", spec.getType() == ChartSpec.Type.STACKED_BAR);
        // Long category names read better on horizontal bars.
        chart.put("horizontal", spec.getType() == ChartSpec.Type.BAR);
        return chart;
    }

    private static void send(HttpServletResponse response, int status, Map<String, ?> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(JsonWriter.toJson(body));
    }
}
