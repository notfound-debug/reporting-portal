package com.reportingportal.export;

import java.util.Date;

/** One row of export_runs with its schedule's owner and report. A class with getters for exports.jsp. */
public final class ExportRun {

    private final long runId;
    private final long scheduleId;
    private final long ownerUserId;
    private final String ownerUsername;
    private final String reportCode;
    private final String status;
    private final Date startedAt;
    private final Date finishedAt;
    private final Long rowCount;
    private final String fileName;
    private final Long fileBytes;
    private final String errorMessage;

    public ExportRun(long runId, long scheduleId, long ownerUserId, String ownerUsername, String reportCode,
                     String status, Date startedAt, Date finishedAt, Long rowCount, String fileName,
                     Long fileBytes, String errorMessage) {
        this.runId = runId;
        this.scheduleId = scheduleId;
        this.ownerUserId = ownerUserId;
        this.ownerUsername = ownerUsername;
        this.reportCode = reportCode;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.rowCount = rowCount;
        this.fileName = fileName;
        this.fileBytes = fileBytes;
        this.errorMessage = errorMessage;
    }

    public long getRunId() {
        return runId;
    }

    public long getScheduleId() {
        return scheduleId;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getReportCode() {
        return reportCode;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public Date getStartedAt() {
        return startedAt;
    }

    public Date getFinishedAt() {
        return finishedAt;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getFileBytes() {
        return fileBytes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
