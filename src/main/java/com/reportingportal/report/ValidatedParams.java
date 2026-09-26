package com.reportingportal.report;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * A report's parameters after validation: every value here has passed its rule
 * and has already been converted to the Java type that is bound to the SQL
 * (java.sql.Date, Integer, String), or, for COLUMN parameters, to a whitelisted
 * column name. Only parameters that are present are included.
 *
 * It also keeps the canonical text form of the parameters, used to build the
 * paging and sorting links, and later stored with a scheduled export.
 */
public final class ValidatedParams {

    private final Map<String, Object> values;
    private final Map<String, String> canonical;
    private final String sortColumn;
    private final SortDirection direction;
    private final int page;

    ValidatedParams(Map<String, Object> values, Map<String, String> canonical,
                    String sortColumn, SortDirection direction, int page) {
        this.values = Map.copyOf(values);
        this.canonical = new LinkedHashMap<>(canonical);
        this.sortColumn = sortColumn;
        this.direction = direction;
        this.page = page;
    }

    /** The typed value of a parameter, or null if it was not given. */
    public Object value(String paramName) {
        return values.get(paramName);
    }

    /** For a COLUMN parameter: the whitelisted column name chosen. */
    public String column(String paramName) {
        return (String) values.get(paramName);
    }

    /** The whitelisted ORDER BY column, or null for a report without user sorting. */
    public String getSortColumn() {
        return sortColumn;
    }

    public SortDirection getDirection() {
        return direction;
    }

    public int getPage() {
        return page;
    }

    /**
     * The validated parameters as a URL query string, in definition order, without
     * the page number, e.g. "from_month=2017-01&sort=revenue&dir=desc".
     */
    public String toQueryString() {
        return toQueryString(canonical);
    }

    /** The same parameters with some values replaced or added, e.g. a different sort or page. */
    public String toQueryStringWith(Map<String, String> changes) {
        Map<String, String> copy = new LinkedHashMap<>(canonical);
        copy.putAll(changes);
        return toQueryString(copy);
    }

    private static String toQueryString(Map<String, String> parameters) {
        StringJoiner joiner = new StringJoiner("&");
        parameters.forEach((name, value) -> joiner.add(
                URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return joiner.toString();
    }
}
