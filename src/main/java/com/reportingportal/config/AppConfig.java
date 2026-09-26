package com.reportingportal.config;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * All settings the portal needs, read once at startup from environment variables.
 *
 * Environment variables (not a file inside the WAR) keep credentials out of the
 * build: docker compose passes them in from the gitignored .env file.
 * Anything missing or malformed stops the deployment immediately with a message
 * that names the variable, instead of failing later on the first request.
 */
public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        int poolSize,
        Path exportDir,
        ZoneId timeZone,
        int schedulerIntervalSeconds) {

    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    /** Takes the variables as a map so the parsing can be tested without a real environment. */
    static AppConfig from(Map<String, String> env) {
        List<String> problems = new ArrayList<>();

        String dbUrl = required(env, "PORTAL_DB_URL", problems);
        String dbUser = required(env, "PORTAL_DB_USER", problems);
        String dbPassword = required(env, "PORTAL_DB_PASSWORD", problems);
        int poolSize = intInRange(env, "PORTAL_DB_POOL_SIZE", 5, 1, 20, problems);
        String exportDir = env.getOrDefault("PORTAL_EXPORT_DIR", "/exports");
        ZoneId timeZone = zone(env, "PORTAL_TIME_ZONE", problems);
        int interval = intInRange(env, "PORTAL_SCHEDULER_INTERVAL_SECONDS", 30, 5, 3600, problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Portal configuration is invalid: " + String.join("; ", problems));
        }
        return new AppConfig(dbUrl, dbUser, dbPassword, poolSize, Path.of(exportDir), timeZone, interval);
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

    /** Never print the password, e.g. when the config is logged. */
    @Override
    public String toString() {
        return "AppConfig[dbUrl=" + dbUrl + ", dbUser=" + dbUser + ", poolSize=" + poolSize
                + ", exportDir=" + exportDir + ", timeZone=" + timeZone
                + ", schedulerIntervalSeconds=" + schedulerIntervalSeconds + "]";
    }
}
