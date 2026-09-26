package com.reportingportal.export;

import java.sql.Timestamp;

/**
 * One row of export_schedules, with its owner's username and whether the owner
 * is active. A class with getters so exports.jsp can read it (EL 5.0).
 * nextRunAt is kept exactly as read (a java.sql.Timestamp, which is a java.util.Date, so
 * JSTL's fmt:formatDate can show it): the scheduler's claim compares it for equality.
 */
public final class ExportSchedule {

    private final long scheduleId;
    private final long userId;
    private final String ownerUsername;
    private final boolean ownerActive;
    private final String reportCode;
    private final String paramsQuery;
    private final String runTime;
    private final Timestamp nextRunAt;
    private final boolean enabled;

    public ExportSchedule(long scheduleId, long userId, String ownerUsername, boolean ownerActive,
                          String reportCode, String paramsQuery, String runTime, Timestamp nextRunAt, boolean enabled) {
        this.scheduleId = scheduleId;
        this.userId = userId;
        this.ownerUsername = ownerUsername;
        this.ownerActive = ownerActive;
        this.reportCode = reportCode;
        this.paramsQuery = paramsQuery;
        this.runTime = runTime;
        this.nextRunAt = nextRunAt;
        this.enabled = enabled;
    }

    public long getScheduleId() {
        return scheduleId;
    }

    public long getUserId() {
        return userId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public boolean isOwnerActive() {
        return ownerActive;
    }

    public String getReportCode() {
        return reportCode;
    }

    public String getParamsQuery() {
        return paramsQuery;
    }

    public String getRunTime() {
        return runTime;
    }

    public Timestamp getNextRunAt() {
        return nextRunAt;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
