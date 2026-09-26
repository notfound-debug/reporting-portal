package com.reportingportal.export;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * export_runs in the PORTAL schema: one row per export attempt.
 * RUNNING while it runs, then SUCCESS (with the file) or FAILED (with the reason).
 */
public class ExportRunDao {

    /** Longest error text stored; the column is VARCHAR2(1000). */
    static final int MAX_ERROR_LENGTH = 1000;

    private static final String SELECT_RUN =
            "SELECT r.run_id, r.schedule_id, s.user_id, u.username, s.report_code, r.status, "
          + "       r.started_at, r.finished_at, r.row_count, r.file_name, r.file_bytes, r.error_message "
          + "FROM export_runs r "
          + "JOIN export_schedules s ON s.schedule_id = r.schedule_id "
          + "JOIN users u ON u.user_id = s.user_id ";

    private final DataSource portal;

    public ExportRunDao(DataSource portal) {
        this.portal = portal;
    }

    public long start(long scheduleId, Instant now) throws SQLException {
        String sql = "INSERT INTO export_runs (schedule_id, status, started_at) VALUES (?, 'RUNNING', ?)";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, new String[] {"run_id"})) {
            statement.setLong(1, scheduleId);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void succeed(long runId, long rowCount, String fileName, long fileBytes, Instant now) throws SQLException {
        String sql = "UPDATE export_runs SET status = 'SUCCESS', finished_at = ?, row_count = ?, "
                   + "file_name = ?, file_bytes = ? WHERE run_id = ?";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setLong(2, rowCount);
            statement.setString(3, fileName);
            statement.setLong(4, fileBytes);
            statement.setLong(5, runId);
            statement.executeUpdate();
        }
    }

    public void fail(long runId, String message, Instant now) throws SQLException {
        String sql = "UPDATE export_runs SET status = 'FAILED', finished_at = ?, error_message = ? WHERE run_id = ?";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, truncate(message));
            statement.setLong(3, runId);
            statement.executeUpdate();
        }
    }

    /**
     * Called once at startup: any run still RUNNING was cut off when the portal
     * last stopped (a single portal instance, so nobody else can be running it).
     */
    public int failInterruptedRuns(Instant now) throws SQLException {
        String sql = "UPDATE export_runs SET status = 'FAILED', finished_at = ?, "
                   + "error_message = 'Interrupted: the portal stopped while this export was running.' "
                   + "WHERE status = 'RUNNING'";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            return statement.executeUpdate();
        }
    }

    /** The latest runs of the user's schedules, or of everyone's for an admin. */
    public List<ExportRun> listRecent(long userId, boolean allUsers, int limit) throws SQLException {
        String sql = SELECT_RUN
                   + "WHERE (? = 'Y' OR s.user_id = ?) "
                   + "ORDER BY r.run_id DESC FETCH FIRST ? ROWS ONLY";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, allUsers ? "Y" : "N");
            statement.setLong(2, userId);
            statement.setInt(3, limit);
            return readAll(statement);
        }
    }

    public Optional<ExportRun> find(long runId) throws SQLException {
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_RUN + "WHERE r.run_id = ?")) {
            statement.setLong(1, runId);
            List<ExportRun> found = readAll(statement);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
    }

    private static List<ExportRun> readAll(PreparedStatement statement) throws SQLException {
        List<ExportRun> runs = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                runs.add(new ExportRun(
                        rs.getLong("run_id"),
                        rs.getLong("schedule_id"),
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("report_code"),
                        rs.getString("status"),
                        rs.getTimestamp("started_at"),
                        rs.getTimestamp("finished_at"),
                        nullableLong(rs, "row_count"),
                        rs.getString("file_name"),
                        nullableLong(rs, "file_bytes"),
                        rs.getString("error_message")));
            }
        }
        return runs;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String truncate(String message) {
        String text = message == null ? "Unknown error" : message;
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH - 3) + "...";
    }
}
