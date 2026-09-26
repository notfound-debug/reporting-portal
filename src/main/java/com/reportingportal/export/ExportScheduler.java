package com.reportingportal.export;

import com.reportingportal.auth.UserDao;
import com.reportingportal.report.AccessPolicy;
import com.reportingportal.report.ParamValidator;
import com.reportingportal.report.ReportDao;
import com.reportingportal.report.ReportDefinition;
import com.reportingportal.report.ReportQuery;
import com.reportingportal.report.ReportRegistry;
import com.reportingportal.report.ReportResult;
import com.reportingportal.report.ValidationResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The background job that runs scheduled CSV exports, inside the web app.
 *
 * AppContextListener starts it when the web app starts and stops it when the
 * web app stops. It has one thread, which wakes every PORTAL_SCHEDULER_INTERVAL_SECONDS
 * (30 s) and runs every enabled schedule whose next_run_at has passed.
 *
 * For each due schedule:
 *   1. claim it (move next_run_at to the next day) with a conditional UPDATE, so it runs once;
 *   2. insert an export_runs row, status RUNNING;
 *   3. check again that the owner may see the report, and re-validate the stored parameters;
 *   4. run the report through the same ReportQuery as the web pages, all rows up to a cap;
 *   5. write the CSV to a temporary file, then rename it, so a download never sees half a file;
 *   6. mark the run SUCCESS, or FAILED with the reason. A failed run is not retried;
 *      the schedule simply runs again at its next daily time.
 */
public class ExportScheduler {

    private static final Logger LOG = Logger.getLogger(ExportScheduler.class.getName());

    /** An export that would be larger than this fails instead of writing a cut-off file. */
    static final int MAX_EXPORT_ROWS = 100_000;

    /** Thrown for expected failures whose message is shown to the user as the run's reason. */
    private static final class ExportFailure extends Exception {
        ExportFailure(String message) {
            super(message);
        }
    }

    private final ScheduleDao schedules;
    private final ExportRunDao runs;
    private final UserDao users;
    private final ReportRegistry registry;
    private final List<String> categories;
    private final ReportDao reportDao;
    private final Path exportDir;
    private final ZoneId zone;
    private final int intervalSeconds;
    private final ScheduledExecutorService executor;

    public ExportScheduler(ScheduleDao schedules, ExportRunDao runs, UserDao users, ReportRegistry registry,
                           List<String> categories, ReportDao reportDao, Path exportDir, ZoneId zone,
                           int intervalSeconds) {
        this.schedules = schedules;
        this.runs = runs;
        this.users = users;
        this.registry = registry;
        this.categories = categories;
        this.reportDao = reportDao;
        this.exportDir = exportDir;
        this.zone = zone;
        this.intervalSeconds = intervalSeconds;
        // One named daemon thread: exports run one at a time, and the thread never
        // keeps the JVM alive on its own.
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "export-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        // Fixed DELAY, not fixed rate: the next wait starts after a tick finishes,
        // so ticks can never pile up behind a slow export.
        executor.scheduleWithFixedDelay(this::tick, 10, intervalSeconds, TimeUnit.SECONDS);
        LOG.info("Export scheduler started: checks every " + intervalSeconds + " s, time zone " + zone
                + ", files in " + exportDir);
    }

    /** Called before the connection pools close, so a running export can still finish. */
    public void stop() {
        executor.shutdown();                      // no new ticks
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();           // interrupt a tick that is still running
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("Export scheduler stopped");
    }

    /**
     * One pass over the due schedules. The whole body is inside try/catch because
     * a ScheduledExecutorService silently stops rescheduling a task that throws:
     * one unexpected error would otherwise end all future exports without a trace.
     */
    void tick() {
        try {
            Instant now = Instant.now();
            for (ExportSchedule schedule : schedules.findDue(now)) {
                Instant next = NextRunCalculator.nextRun(LocalTime.parse(schedule.getRunTime()), now, zone);
                if (schedules.claim(schedule.getScheduleId(), schedule.getNextRunAt(), next)) {
                    runExport(schedule);
                } else {
                    LOG.info("Schedule " + schedule.getScheduleId() + " was already claimed; skipped");
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Export scheduler tick failed; will try again next tick", t);
        }
    }

    private void runExport(ExportSchedule schedule) throws Exception {
        long runId = runs.start(schedule.getScheduleId(), Instant.now());
        Path tempFile = exportDir.resolve(ExportFiles.tempFileName(runId));
        try {
            ReportDefinition report = checkedReport(schedule);
            ValidationResult validation = ParamValidator.validate(
                    report, QueryStrings.parse(schedule.getParamsQuery()), categories);
            if (!validation.isValid()) {
                throw new ExportFailure("The saved parameters are no longer valid: "
                        + String.join(" ", validation.getErrors().values()));
            }

            ReportResult result = reportDao.run(report,
                    ReportQuery.forAllRows(report, validation.getParams(), MAX_EXPORT_ROWS), MAX_EXPORT_ROWS);
            if (result.isMore()) {
                throw new ExportFailure("The export would have more than " + MAX_EXPORT_ROWS
                        + " rows. Narrow the parameters.");
            }

            try (BufferedWriter out = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                CsvWriter.write(result.getColumns(), result.getRows(), out);
            }
            String fileName = ExportFiles.fileName(report.getCode(), Instant.now().atZone(zone), runId);
            Path finalFile = ExportFiles.resolveInside(exportDir, fileName);
            // A rename within one folder: the file appears complete or not at all.
            Files.move(tempFile, finalFile, StandardCopyOption.ATOMIC_MOVE);

            runs.succeed(runId, result.getRowCount(), fileName, Files.size(finalFile), Instant.now());
            LOG.info("Export run " + runId + " (schedule " + schedule.getScheduleId() + ", " + report.getCode()
                    + ") wrote " + result.getRowCount() + " rows to " + fileName);
        } catch (ExportFailure e) {
            recordFailure(runId, schedule, tempFile, e.getMessage(), null);
        } catch (Exception e) {
            // Unexpected (database, disk ...): a short reason for the user, the details in the log.
            recordFailure(runId, schedule, tempFile, e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * The report, if the schedule's owner is still active and still holds a role
     * that may see it. Roles are read from the database at every run, not from a
     * session: someone whose access was removed must stop receiving the data.
     */
    private ReportDefinition checkedReport(ExportSchedule schedule) throws Exception {
        if (!schedule.isOwnerActive()) {
            throw new ExportFailure("The schedule owner's account is disabled.");
        }
        ReportDefinition report = registry.find(schedule.getReportCode())
                .orElseThrow(() -> new ExportFailure("The report no longer exists."));
        Set<String> ownerRoles = users.findRoles(schedule.getUserId());
        if (!AccessPolicy.canView(ownerRoles, report)) {
            throw new ExportFailure("The schedule owner no longer has access to this report.");
        }
        return report;
    }

    private void recordFailure(long runId, ExportSchedule schedule, Path tempFile, String reason, Exception cause) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete " + tempFile, e);
        }
        LOG.log(Level.WARNING, "Export run " + runId + " (schedule " + schedule.getScheduleId() + ") failed: " + reason, cause);
        try {
            runs.fail(runId, reason, Instant.now());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Could not record the failure of export run " + runId, e);
        }
    }
}
