package com.reportingportal.report;

/**
 * How a result column is displayed on the page and written to CSV/JSON.
 * YEAR exists so that 2017 is not shown with a thousands separator ("2,017").
 */
public enum ColumnType {
    TEXT,
    INTEGER,
    YEAR,
    MONEY,
    PERCENT,
    DATE,
    MONTH
}
