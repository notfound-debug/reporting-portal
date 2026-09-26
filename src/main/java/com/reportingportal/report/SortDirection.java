package com.reportingportal.report;

/**
 * Sort direction. The request says "asc" or "desc"; the SQL comes only from
 * these constants. NULLS LAST is explicit because Oracle puts NULLs first when
 * sorting in descending order.
 */
public enum SortDirection {
    ASC("asc", "ASC NULLS LAST"),
    DESC("desc", "DESC NULLS LAST");

    private final String parameterValue;
    private final String sql;

    SortDirection(String parameterValue, String sql) {
        this.parameterValue = parameterValue;
        this.sql = sql;
    }

    public String getParameterValue() {
        return parameterValue;
    }

    public String sql() {
        return sql;
    }

    public SortDirection opposite() {
        return this == ASC ? DESC : ASC;
    }

    /** Returns null for anything other than exactly "asc" or "desc". */
    public static SortDirection fromParameter(String value) {
        for (SortDirection direction : values()) {
            if (direction.parameterValue.equals(value)) {
                return direction;
            }
        }
        return null;
    }
}
