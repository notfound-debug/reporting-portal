package com.reportingportal.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the report codes and access rules from db/02_seed.sql, so the tests check
 * the real seed data instead of a copy of it. Maven runs tests from the project root.
 */
final class SeedFile {

    private static final Pattern REPORT = Pattern.compile(
            "INSERT INTO reports \\(report_code\\) VALUES \\('([a-z0-9_]+)'\\)");
    private static final Pattern REPORT_ROLE = Pattern.compile(
            "INSERT INTO report_roles \\(report_code, role_code\\) VALUES \\('([a-z0-9_]+)',\\s*'([A-Z]+)'\\)");

    private SeedFile() {
    }

    /** Report code -> roles, exactly as db/02_seed.sql inserts them. */
    static Map<String, Set<String>> rolesByReport() throws IOException {
        String sql = Files.readString(Path.of("db", "02_seed.sql"));
        Map<String, Set<String>> roles = new HashMap<>();
        Matcher reports = REPORT.matcher(sql);
        while (reports.find()) {
            roles.put(reports.group(1), new HashSet<>());
        }
        Matcher reportRoles = REPORT_ROLE.matcher(sql);
        while (reportRoles.find()) {
            roles.get(reportRoles.group(1)).add(reportRoles.group(2));
        }
        return roles;
    }

    /** The registry as the running app builds it: Java definitions plus the seeded roles. */
    static ReportRegistry registry() throws IOException {
        return new ReportRegistry(ReportRegistry.definitions(), rolesByReport());
    }
}
