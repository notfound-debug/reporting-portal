package com.reportingportal.export;

import com.reportingportal.auth.AuthenticatedUser;
import com.reportingportal.config.AppConfig;
import com.reportingportal.config.AppContextListener;
import com.reportingportal.report.AccessPolicy;
import com.reportingportal.report.ParamValidator;
import com.reportingportal.report.ReportDefinition;
import com.reportingportal.report.ReportRegistry;
import com.reportingportal.report.ValidationResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * /exports: the user's export schedules and recent runs (an admin sees everyone's).
 *
 *   GET                         the list page
 *   POST action=create          new daily schedule (from the form on a report page)
 *   POST action=runNow          make a schedule due now; the scheduler runs it within ~30 s
 *   POST action=disable         stop a schedule (kept, so its run history stays)
 *
 * Every POST has passed CsrfFilter. A successful POST redirects back to GET /exports
 * (Post/Redirect/Get); a rejected one shows the page again with a message and status 400.
 */
@WebServlet("/exports")
public class ExportServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ExportServlet.class.getName());

    private static final String VIEW = "/WEB-INF/jsp/exports.jsp";

    /** A user may have at most this many enabled schedules. */
    static final int MAX_SCHEDULES_PER_USER = 10;

    private static final int RUNS_SHOWN = 50;
    private static final Pattern RUN_TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final Pattern ID = Pattern.compile("^\\d{1,18}$");

    private ReportRegistry registry;
    private List<String> categories;
    private AppConfig config;
    private ScheduleDao schedules;
    private ExportRunDao runs;

    @Override
    @SuppressWarnings("unchecked")
    public void init() {
        registry = (ReportRegistry) getServletContext().getAttribute(AppContextListener.REGISTRY);
        categories = (List<String>) getServletContext().getAttribute(AppContextListener.CATEGORIES);
        config = (AppConfig) getServletContext().getAttribute(AppContextListener.CONFIG);
        DataSource portal = (DataSource) getServletContext().getAttribute(AppContextListener.DATA_SOURCE);
        schedules = new ScheduleDao(portal);
        runs = new ExportRunDao(portal);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Fixed messages chosen by a flag in the URL after a redirect, never text from the URL itself.
        if (request.getParameter("created") != null) {
            request.setAttribute("message", "Schedule created.");
        } else if (request.getParameter("queued") != null) {
            request.setAttribute("message", "Export queued: it will run within about "
                    + config.schedulerIntervalSeconds() + " seconds. Reload this page to see it.");
        } else if (request.getParameter("disabled") != null) {
            request.setAttribute("message", "Schedule disabled.");
        }
        showPage(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
        String action = request.getParameter("action");
        try {
            if ("create".equals(action)) {
                create(request, response, user);
            } else if ("runNow".equals(action) || "disable".equals(action)) {
                changeSchedule(request, response, user, action);
            } else {
                reject(request, response, "Unknown action.");
            }
        } catch (SQLException e) {
            throw new ServletException("Export schedule change failed", e);
        }
    }

    private void create(HttpServletRequest request, HttpServletResponse response, AuthenticatedUser user)
            throws ServletException, IOException, SQLException {
        Optional<ReportDefinition> found = registry.find(nullToEmpty(request.getParameter("report_code")));
        if (found.isEmpty()) {
            reject(request, response, "Unknown report.");
            return;
        }
        ReportDefinition report = found.get();
        // Same access rule as viewing: you cannot schedule what you may not see.
        if (!AccessPolicy.canView(user, report)) {
            LOG.warning("Access denied: user=" + user.getUsername() + " report=" + report.getCode() + " via=schedule");
            request.setAttribute("errorMessage", "You do not have access to this report.");
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // The parameters arrive as the query string the report page showed. Validate them
        // exactly like a live request, and store the canonical form, not what was posted.
        String posted = nullToEmpty(request.getParameter("params"));
        if (posted.length() > 1000) {
            reject(request, response, "The report parameters are too long.");
            return;
        }
        ValidationResult validation = ParamValidator.validate(report, QueryStrings.parse(posted), categories);
        if (!validation.isValid()) {
            reject(request, response, "The report parameters are not valid: "
                    + String.join(" ", validation.getErrors().values()));
            return;
        }

        String runTime = nullToEmpty(request.getParameter("run_time")).trim();
        if (!RUN_TIME.matcher(runTime).matches()) {
            reject(request, response, "Daily time must be written HH:MM, from 00:00 to 23:59.");
            return;
        }
        if (schedules.countEnabled(user.getUserId()) >= MAX_SCHEDULES_PER_USER) {
            reject(request, response, "You already have " + MAX_SCHEDULES_PER_USER
                    + " active schedules. Disable one before adding another.");
            return;
        }

        Instant nextRun = NextRunCalculator.nextRun(LocalTime.parse(runTime), Instant.now(), config.timeZone());
        long id = schedules.create(user.getUserId(), report.getCode(),
                validation.getParams().toQueryString(), runTime, nextRun);
        LOG.info("Schedule " + id + " created: user=" + user.getUsername() + " report=" + report.getCode()
                + " daily at " + runTime + " " + config.timeZone());
        response.sendRedirect(request.getContextPath() + "/exports?created");
    }

    private void changeSchedule(HttpServletRequest request, HttpServletResponse response,
                                AuthenticatedUser user, String action)
            throws ServletException, IOException, SQLException {
        String idText = nullToEmpty(request.getParameter("schedule_id"));
        Optional<ExportSchedule> found = ID.matcher(idText).matches()
                ? schedules.find(Long.parseLong(idText))
                : Optional.empty();
        // Someone else's schedule looks exactly like a missing one.
        if (found.isEmpty() || !(found.get().getUserId() == user.getUserId() || user.isAdmin())) {
            reject(request, response, "Schedule not found.");
            return;
        }
        ExportSchedule schedule = found.get();
        if ("runNow".equals(action)) {
            if (!schedules.makeDueNow(schedule.getScheduleId(), Instant.now())) {
                reject(request, response, "Only an enabled schedule can be run.");
                return;
            }
            LOG.info("Schedule " + schedule.getScheduleId() + " queued to run now by " + user.getUsername());
            response.sendRedirect(request.getContextPath() + "/exports?queued");
        } else {
            schedules.disable(schedule.getScheduleId());
            LOG.info("Schedule " + schedule.getScheduleId() + " disabled by " + user.getUsername());
            response.sendRedirect(request.getContextPath() + "/exports?disabled");
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message)
            throws ServletException, IOException {
        request.setAttribute("formError", message);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        showPage(request, response);
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
        try {
            request.setAttribute("schedules", schedules.list(user.getUserId(), user.isAdmin()));
            request.setAttribute("runs", runs.listRecent(user.getUserId(), user.isAdmin(), RUNS_SHOWN));
        } catch (SQLException e) {
            throw new ServletException("Could not load exports", e);
        }
        Map<String, String> reportTitles = new LinkedHashMap<>();
        registry.all().forEach(report -> reportTitles.put(report.getCode(), report.getTitle()));
        request.setAttribute("reportTitles", reportTitles);
        request.setAttribute("showOwner", user.isAdmin());
        request.setAttribute("timeZone", config.timeZone().getId());
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
