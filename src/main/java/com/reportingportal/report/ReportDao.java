package com.reportingportal.report;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a ReportQuery against the warehouse pool (the read-only reporting user)
 * and reads the rows into plain Java values.
 */
public class ReportDao {

    private static final Logger LOG = Logger.getLogger(ReportDao.class.getName());

    /** A report query that runs longer than this is cancelled, so it cannot hold a connection forever. */
    static final int QUERY_TIMEOUT_SECONDS = 30;

    /**
     * Rows fetched per network round trip. The Oracle driver's default is 10,
     * so reading 6,000 rows would take 600 round trips; with 100 it takes 60.
     */
    static final int FETCH_SIZE = 100;

    private final DataSource warehouse;

    public ReportDao(DataSource warehouse) {
        this.warehouse = warehouse;
    }

    /**
     * Runs the query and returns at most keepRows rows. If the database returned
     * more (the query always asks for one extra), the result is marked "more".
     */
    public ReportResult run(ReportDefinition report, ReportQuery query, int keepRows) throws SQLException {
        long start = System.nanoTime();
        List<List<Object>> rows = new ArrayList<>();

        // try-with-resources closes the result set, the statement and the connection
        // (returning it to the pool) even when the query fails.
        try (Connection connection = warehouse.getConnection();
             PreparedStatement statement = connection.prepareStatement(query.sql())) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setFetchSize(FETCH_SIZE);
            List<Object> binds = query.binds();
            for (int i = 0; i < binds.size(); i++) {
                statement.setObject(i + 1, binds.get(i));    // JDBC parameters are numbered from 1
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readRow(rs, report.getColumns()));
                }
            }
        }

        boolean more = rows.size() > keepRows;
        if (more) {
            rows = new ArrayList<>(rows.subList(0, keepRows));
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("SQL: " + query.sql() + " binds=" + query.binds());
        }
        LOG.info("report=" + report.getCode() + " rows=" + rows.size() + (more ? "+" : "")
                + " ms=" + (System.nanoTime() - start) / 1_000_000);
        return new ReportResult(report.getColumns(), Collections.unmodifiableList(rows), more);
    }

    /** Reads the columns by position, converting each by its declared type. */
    private static List<Object> readRow(ResultSet rs, List<ColumnDef> columns) throws SQLException {
        List<Object> row = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            int position = i + 1;
            switch (columns.get(i).getType()) {
                case TEXT:
                    row.add(rs.getString(position));
                    break;
                case DATE: {
                    Date date = rs.getDate(position);
                    row.add(date == null ? null : date.toLocalDate());
                    break;
                }
                case MONTH: {
                    Date date = rs.getDate(position);
                    row.add(date == null ? null : YearMonth.from(date.toLocalDate()));
                    break;
                }
                default:                                     // INTEGER, YEAR, MONEY, PERCENT
                    row.add(rs.getBigDecimal(position));
            }
        }
        return row;
    }
}
