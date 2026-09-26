package com.reportingportal.export;

import com.reportingportal.auth.AuthenticatedUser;
import com.reportingportal.config.AppConfig;
import com.reportingportal.config.AppContextListener;
import com.reportingportal.report.AccessPolicy;
import com.reportingportal.report.ReportDefinition;
import com.reportingportal.report.ReportRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * GET /exports/download?run={id}: downloads the CSV file of one successful export run.
 *
 * Path-traversal protection: the request carries only a numeric run id. The file
 * name comes from export_runs (written by the server), is checked against the
 * generated-name pattern, and must resolve to a file directly inside the export
 * folder. A value like "../../etc/passwd" is not a number and is rejected at once.
 *
 * Access: the run's owner or an admin, and only if they may still see the report.
 * Anything else gets 404, so run ids cannot be probed to learn which ones exist.
 */
@WebServlet("/exports/download")
public class ExportDownloadServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ExportDownloadServlet.class.getName());

    private static final Pattern RUN_ID = Pattern.compile("^\\d{1,18}$");

    private ReportRegistry registry;
    private AppConfig config;
    private ExportRunDao runs;

    @Override
    public void init() {
        registry = (ReportRegistry) getServletContext().getAttribute(AppContextListener.REGISTRY);
        config = (AppConfig) getServletContext().getAttribute(AppContextListener.CONFIG);
        runs = new ExportRunDao((DataSource) getServletContext().getAttribute(AppContextListener.DATA_SOURCE));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String runText = request.getParameter("run");
        if (runText == null || !RUN_ID.matcher(runText).matches()) {
            request.setAttribute("errorMessage", "The export id must be a number.");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
        Optional<ExportRun> found;
        try {
            found = runs.find(Long.parseLong(runText));
        } catch (SQLException e) {
            throw new ServletException("Could not look up export run " + runText, e);
        }
        if (found.isEmpty() || !mayDownload(user, found.get())) {
            notFound(request, response);
            return;
        }

        ExportRun run = found.get();
        Path file = ExportFiles.resolveInside(config.exportDir(), run.getFileName());
        if (!Files.isRegularFile(file)) {
            LOG.warning("Export run " + run.getRunId() + " is SUCCESS but its file is missing: " + file);
            notFound(request, response);
            return;
        }

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + run.getFileName() + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
        LOG.info("Download: user=" + user.getUsername() + " run=" + run.getRunId() + " file=" + run.getFileName());
    }

    private boolean mayDownload(AuthenticatedUser user, ExportRun run) {
        if (!run.isSuccess()) {
            return false;
        }
        if (run.getOwnerUserId() != user.getUserId() && !user.isAdmin()) {
            return false;
        }
        Optional<ReportDefinition> report = registry.find(run.getReportCode());
        return report.isPresent() && AccessPolicy.canView(user, report.get());
    }

    private static void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setAttribute("errorMessage", "Export not found.");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
