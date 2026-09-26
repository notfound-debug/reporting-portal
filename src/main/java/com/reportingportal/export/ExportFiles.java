package com.reportingportal.export;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Names of export files, and the check that a file name stays inside the export folder.
 *
 * File names are generated only by the server, from the report code, the time
 * and the run id, e.g. monthly_revenue_20260926_063000_run12.csv. They never
 * contain anything a user typed. Downloads look up the name in export_runs by
 * a numeric run id; the browser never sends a file name or path.
 */
public final class ExportFiles {

    static final Pattern FILE_NAME = Pattern.compile("^[a-z][a-z0-9_]*_\\d{8}_\\d{6}_run\\d+\\.csv$");

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private ExportFiles() {
    }

    public static String fileName(String reportCode, ZonedDateTime when, long runId) {
        return reportCode + "_" + when.format(STAMP) + "_run" + runId + ".csv";
    }

    /** The temporary name used while a file is being written; never offered for download. */
    static String tempFileName(long runId) {
        return ".tmp-run" + runId + ".csv";
    }

    /**
     * The full path of an export file, refusing anything that is not a generated
     * file name or that would end up outside the export folder (e.g. "../../etc/passwd").
     * Defence in depth: names come from the database, which only the server writes.
     */
    public static Path resolveInside(Path exportDir, String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Not an export file name: " + fileName);
        }
        Path folder = exportDir.toAbsolutePath().normalize();
        Path file = folder.resolve(fileName).normalize();
        if (!file.getParent().equals(folder)) {
            throw new IllegalArgumentException("Export file outside the export folder: " + fileName);
        }
        return file;
    }
}
