package com.reportingportal.report;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Builds the SELECT statement for a report and the list of values to bind.
 *
 * The rule that makes this injection-safe: every piece of SQL text comes from
 * constants in the ReportDefinition (view name, column names, WHERE fragments,
 * the whitelisted sort column, the SortDirection enum). Values typed by the user
 * only ever travel in the bind list and reach Oracle through
 * PreparedStatement.setObject, one per "?". Oracle never parses them as SQL.
 *
 * Shape of the generated statement:
 *   SELECT <columns> FROM <view>
 *   [WHERE <fragment> AND <fragment> ...]          only for parameters that are present
 *   ORDER BY <sort column> <direction>, <tie-breakers> ASC
 *   OFFSET ? ROWS FETCH NEXT ? ROWS ONLY           one page (reads one extra row)
 *   or FETCH FIRST ? ROWS ONLY                      top-N, chart and export
 */
public final class ReportQuery {

    /** Rows shown on one page of an HTML report. */
    public static final int PAGE_SIZE = 50;

    private final String sql;
    private final List<Object> binds;

    private ReportQuery(String sql, List<Object> binds) {
        this.sql = sql;
        this.binds = List.copyOf(binds);
    }

    public String sql() {
        return sql;
    }

    public List<Object> binds() {
        return binds;
    }

    /**
     * One page for the HTML table. Asks for PAGE_SIZE + 1 rows: if the extra row
     * comes back there is a next page, and no COUNT(*) query is needed.
     */
    public static ReportQuery forPage(ReportDefinition report, ValidatedParams params) {
        List<Object> binds = new ArrayList<>();
        StringBuilder sql = selectFromWhere(report, params, binds);
        sql.append(orderBy(report, params));

        Integer rowLimit = rowLimit(report, params);
        if (rowLimit != null) {
            sql.append(" FETCH FIRST ? ROWS ONLY");
            binds.add(rowLimit);
        } else if (report.isPaged()) {
            sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            binds.add((params.getPage() - 1) * PAGE_SIZE);
            binds.add(PAGE_SIZE + 1);
        }
        return new ReportQuery(sql.toString(), binds);
    }

    /**
     * All rows, for a chart or a CSV export, up to maxRows. Asks for one row more
     * than the cap, so the caller can tell "exactly maxRows" from "cut off".
     */
    public static ReportQuery forAllRows(ReportDefinition report, ValidatedParams params, int maxRows) {
        List<Object> binds = new ArrayList<>();
        StringBuilder sql = selectFromWhere(report, params, binds);
        sql.append(orderBy(report, params));

        Integer rowLimit = rowLimit(report, params);
        sql.append(" FETCH FIRST ? ROWS ONLY");
        binds.add(rowLimit != null ? rowLimit : maxRows + 1);
        return new ReportQuery(sql.toString(), binds);
    }

    private static StringBuilder selectFromWhere(ReportDefinition report, ValidatedParams params, List<Object> binds) {
        StringJoiner columns = new StringJoiner(", ");
        for (ColumnDef column : report.getColumns()) {
            columns.add(column.getName());
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(columns).append(" FROM ").append(report.getView());

        List<String> conditions = new ArrayList<>();
        for (ParamDef param : report.getParams()) {
            Object value = params.value(param.getName());
            if (value == null) {
                continue;                              // parameter not given: no filter
            }
            switch (param.getType()) {
                case FLAG:
                    conditions.add(param.getSqlFragment());   // constant condition, nothing to bind
                    break;
                case COLUMN:
                case ROW_LIMIT:
                    break;                                    // used in ORDER BY / FETCH, not in WHERE
                default:
                    conditions.add(param.getSqlFragment());   // e.g. "order_date >= ?"
                    binds.add(value);                         // ...and its value, in the same order
            }
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        return sql;
    }

    private static String orderBy(ReportDefinition report, ValidatedParams params) {
        String leadingColumn;
        String leadingDirection;
        if (report.getOrderByParam() != null) {
            // e.g. category_quarters: largest revenue in the chosen quarter first.
            leadingColumn = params.column(report.getOrderByParam());
            leadingDirection = SortDirection.DESC.sql();
        } else {
            leadingColumn = params.getSortColumn();
            leadingDirection = params.getDirection().sql();
        }
        StringBuilder orderBy = new StringBuilder(" ORDER BY ").append(leadingColumn).append(' ').append(leadingDirection);
        for (String tieBreaker : report.getTieBreakers()) {
            if (!tieBreaker.equals(leadingColumn)) {
                orderBy.append(", ").append(tieBreaker).append(" ASC");
            }
        }
        return orderBy.toString();
    }

    /** The value of the report's ROW_LIMIT (top-N) parameter, or null if it has none. */
    private static Integer rowLimit(ReportDefinition report, ValidatedParams params) {
        for (ParamDef param : report.getParams()) {
            if (param.getType() == ParamType.ROW_LIMIT) {
                return (Integer) params.value(param.getName());
            }
        }
        return null;
    }
}
