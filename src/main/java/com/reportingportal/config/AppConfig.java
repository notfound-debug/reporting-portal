package com.reportingportal.config;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * All settings the portal needs, read once at startup from environment variables.
 *
 * Environment variables (not a file inside the WAR) keep credentials out of the
 * build: docker compose passes them in from the gitignored .env file.
 * Anything missing or malformed stops the deployment immediately with a message
 * that names the variable, instead of failing later on the first request.
 *
 * Two database logins, one per connection pool:
 *   PORTAL_DB_*     the portal's own schema (users, roles, schedules, runs)
 *   WAREHOUSE_DB_*  the warehouse's read-only reporting user, which can only
 *                   SELECT the 12 DW.V_RPT_* views
 */
public record AppConfig(
        String dbUrl,
        String portalUser,
        String portalPassword,
        int portalPoolSize,
        String warehouseUser,
        String warehousePassword,
        String warehouseSchema,
        int warehousePoolSize,
        Path exportDir,
        ZoneId timeZone,
        int schedulerIntervalSeconds) {

    /** An Oracle schema name. It is pasted into ALTER SESSION, so nothing else is accepted. */
    private static final Pattern SCHEMA_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,29}$");

    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    /** Takes the variables as a map so the parsing can be tested without a real environment. */
    static AppConfig from(Map<String, String> env) {
        List<String> problems = new ArrayList<>();

        String dbUrl = required(env, "DB_URL", problems);
        String portalUser = required(env, "PORTAL_DB_USER", problems);
        String portalPassword = required(env, "PORTAL_DB_PASSWORD", problems);
        int portalPoolSize = intInRange(env, "PORTAL_DB_POOL_SIZE", 2, 1, 20, problems);
        String warehouseUser = required(env, "WAREHOUSE_DB_USER", problems);
        String warehousePassword = required(env, "WAREHOUSE_DB_PASSWORD", problems);
        String warehouseSchema = env.getOrDefault("WAREHOUSE_SCHEMA", "DW").trim();
        if (!SCHEMA_NAME.matcher(warehouseSchema).matches()) {
            problems.add("WAREHOUSE_SCHEMA must be a plain Oracle schema name such as DW");
        }
        int warehousePoolSize = intInRange(env, "WAREHOUSE_DB_POOL_SIZE", 5, 1, 20, problems);
        String exportDir = env.getOrDefault("PORTAL_EXPORT_DIR", "/exports");
        ZoneId timeZone = zone(env, "PORTAL_TIME_ZONE", problems);
        int interval = intInRange(env, "PORTAL_SCHEDULER_INTERVAL_SECONDS", 30, 5, 3600, problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Portal configuration is invalid: " + String.join("; ", problems));
        }
        return new AppConfig(dbUrl, portalUser, portalPassword, portalPoolSize,
                warehouseUser, warehousePassword, warehouseSchema, warehousePoolSize,
                Path.of(exportDir), timeZone, interval);
    }

    private static String required(Map<String, String> env, String name, List<String> problems) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            problems.add("missing environment variable " + name);
            return null;
        }
        return value.trim();
    }

    private static int intInRange(Map<String, String> env, String name, int defaultValue,
                                  int min, int max, List<String> problems) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= min && parsed <= max) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the problem message below
        }
        problems.add(name + " must be a whole number from " + min + " to " + max);
        return defaultValue;
    }

    private static ZoneId zone(Map<String, String> env, String name, List<String> problems) {
        String value = env.getOrDefault(name, "UTC");
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException e) {
            problems.add(name + " is not a known time zone, e.g. UTC or Asia/Kolkata");
            return ZoneId.of("UTC");
        }
    }

    /** Never print the passwords, e.g. when the config is logged. */
    @Override
    public String toString() {
        return "AppConfig[dbUrl=" + dbUrl
                + ", portalUser=" + portalUser + ", portalPoolSize=" + portalPoolSize
                + ", warehouseUser=" + warehouseUser + ", warehouseSchema=" + warehouseSchema
                + ", warehousePoolSize=" + warehousePoolSize
                + ", exportDir=" + exportDir + ", timeZone=" + timeZone
                + ", schedulerIntervalSeconds=" + schedulerIntervalSeconds + "]";
    }
}
