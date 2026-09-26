package com.reportingportal.export;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * export_schedules in the PORTAL schema. Every statement is a PreparedStatement
 * with bound values. All times are bound as UTC Timestamps from the JVM's clock
 * (the JVM runs in UTC); the database clock is never used for scheduling.
 * Scheduling times are stored to the whole second (a daily schedule needs no more).
 */
public class ScheduleDao {

    private static final String SELECT_SCHEDULE =
            "SELECT s.schedule_id, s.user_id, u.username, u.is_active, s.report_code, s.params_query, "
          + "       s.run_time, s.next_run_at, s.is_enabled "
          + "FROM export_schedules s JOIN users u ON u.user_id = s.user_id ";

    private final DataSource portal;

    public ScheduleDao(DataSource portal) {
        this.portal = portal;
    }

    /** Inserts a new, enabled schedule and returns its id. */
    public long create(long userId, String reportCode, String paramsQuery, String runTime, Instant nextRunAt)
            throws SQLException {
        String sql = "INSERT INTO export_schedules (user_id, report_code, params_query, run_time, next_run_at) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = portal.getConnection();
             // Ask Oracle to hand back the generated identity value.
             PreparedStatement statement = connection.prepareStatement(sql, new String[] {"schedule_id"})) {
            statement.setLong(1, userId);
            statement.setString(2, reportCode);
            statement.setString(3, paramsQuery);
            statement.setString(4, runTime);
            statement.setTimestamp(5, seconds(nextRunAt));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public int countEnabled(long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM export_schedules WHERE user_id = ? AND is_enabled = 'Y'";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** The user's own schedules, or everyone's for an admin; enabled first, newest first. */
    public List<ExportSchedule> list(long userId, boolean allUsers) throws SQLException {
        String sql = SELECT_SCHEDULE
                   + "WHERE (? = 'Y' OR s.user_id = ?) "
                   + "ORDER BY s.is_enabled DESC, s.schedule_id DESC";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, allUsers ? "Y" : "N");
            statement.setLong(2, userId);
            return readAll(statement);
        }
    }

    public Optional<ExportSchedule> find(long scheduleId) throws SQLException {
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SCHEDULE + "WHERE s.schedule_id = ?")) {
            statement.setLong(1, scheduleId);
            List<ExportSchedule> found = readAll(statement);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
    }

    /** Enabled schedules whose next run time has come, oldest first. */
    public List<ExportSchedule> findDue(Instant now) throws SQLException {
        String sql = SELECT_SCHEDULE
                   + "WHERE s.is_enabled = 'Y' AND s.next_run_at <= ? ORDER BY s.next_run_at";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, seconds(now));
            return readAll(statement);
        }
    }

    /**
     * Claims a due schedule by moving its next run time forward, but only if
     * next_run_at is still the value this scheduler read. If another tick (or
     * another Tomcat) got there first, the WHERE clause matches no row and 0 is
     * returned: optimistic locking, so a schedule can never run twice for one due time.
     * The expected value is bound exactly as it was read, fractions of a second included,
     * so the equality holds even for a row that was not written by this class.
     */
    public boolean claim(long scheduleId, Timestamp expectedNextRunAt, Instant newNextRunAt) throws SQLException {
        String sql = "UPDATE export_schedules SET next_run_at = ? "
                   + "WHERE schedule_id = ? AND next_run_at = ? AND is_enabled = 'Y'";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, seconds(newNextRunAt));
            statement.setLong(2, scheduleId);
            statement.setTimestamp(3, expectedNextRunAt);
            return statement.executeUpdate() == 1;       // auto-commit: the claim is committed at once
        }
    }

    /** "Run now": makes the schedule due immediately; the next scheduler tick runs it. */
    public boolean makeDueNow(long scheduleId, Instant now) throws SQLException {
        String sql = "UPDATE export_schedules SET next_run_at = ? WHERE schedule_id = ? AND is_enabled = 'Y'";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, seconds(now));
            statement.setLong(2, scheduleId);
            return statement.executeUpdate() == 1;
        }
    }

    /** Schedules are disabled, never deleted, so their run history keeps its parent row. */
    public void disable(long scheduleId) throws SQLException {
        String sql = "UPDATE export_schedules SET is_enabled = 'N' WHERE schedule_id = ?";
        try (Connection connection = portal.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, scheduleId);
            statement.executeUpdate();
        }
    }

    private static Timestamp seconds(Instant instant) {
        return Timestamp.from(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    private static List<ExportSchedule> readAll(PreparedStatement statement) throws SQLException {
        List<ExportSchedule> schedules = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                schedules.add(new ExportSchedule(
                        rs.getLong("schedule_id"),
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        "Y".equals(rs.getString("is_active")),
                        rs.getString("report_code"),
                        rs.getString("params_query"),
                        rs.getString("run_time"),
                        rs.getTimestamp("next_run_at"),
                        "Y".equals(rs.getString("is_enabled"))));
            }
        }
        return schedules;
    }
}
