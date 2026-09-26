package com.reportingportal.report;

import java.util.List;

/**
 * The rows of one report run, ready for a JSP, JSON or CSV.
 *
 * Each row is a list of values in column order: String for TEXT,
 * BigDecimal for numbers, LocalDate for DATE and YearMonth for MONTH.
 */
public final class ReportResult {

    private final List<ColumnDef> columns;
    private final List<List<Object>> rows;
    private final boolean more;

    public ReportResult(List<ColumnDef> columns, List<List<Object>> rows, boolean more) {
        this.columns = List.copyOf(columns);
        this.rows = rows;
        this.more = more;
    }

    public List<ColumnDef> getColumns() {
        return columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rows.size();
    }

    /** True if more rows exist than were returned: a next page, or a result cut off at its cap. */
    public boolean isMore() {
        return more;
    }
}
