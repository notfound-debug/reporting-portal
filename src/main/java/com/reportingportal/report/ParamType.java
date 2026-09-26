package com.reportingportal.report;

/**
 * The kinds of report form parameter. Each has its own validation rule in
 * ParamValidator. Only the first seven are ever sent to Oracle, and only as
 * bound values; FLAG and COLUMN never carry user text into the SQL.
 */
public enum ParamType {
    /** YYYY-MM, bound as the first day of the month. */
    MONTH,
    /** YYYY-MM-DD, bound as a date. */
    DATE,
    /** Whole number within the parameter's min..max, bound in a WHERE fragment. */
    INTEGER,
    /** Whole number within min..max, bound as FETCH FIRST ? ROWS ONLY (a top-N limit). */
    ROW_LIMIT,
    /** One of the 27 Brazilian state codes. */
    STATE,
    /** One of a fixed list of values written in the report definition. */
    CHOICE,
    /** One of the product categories read from the warehouse at startup. */
    CATEGORY,
    /** A checkbox: when ticked, a constant SQL fragment is added; nothing is bound. */
    FLAG,
    /** Picks one column from a whitelist; the constant column name is used, nothing is bound. */
    COLUMN
}
