package com.reportingportal.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One parameter on a report form: its name in the URL, its label, its type,
 * and how it reaches the SQL.
 *
 * sqlFragment is a constant written here in code, e.g. "order_date >= ?".
 * When the parameter is present, the fragment is added to the WHERE clause and
 * the validated value is bound to its "?". User input is never part of the
 * fragment text. Create instances with the static factory methods below.
 */
public final class ParamDef {

    private final String name;
    private final String label;
    private final ParamType type;
    private final String sqlFragment;
    private final int min;
    private final int max;
    private final String defaultValue;
    private final List<String> choices;
    /** For COLUMN parameters: option shown to the user -> column name used in SQL. */
    private final Map<String, String> columnOptions;

    private ParamDef(String name, String label, ParamType type, String sqlFragment, int min, int max,
                     String defaultValue, List<String> choices, Map<String, String> columnOptions) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.sqlFragment = sqlFragment;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.choices = List.copyOf(choices);
        this.columnOptions = new LinkedHashMap<>(columnOptions);
    }

    public static ParamDef month(String name, String label, String sqlFragment) {
        return new ParamDef(name, label, ParamType.MONTH, sqlFragment, 0, 0, null, List.of(), Map.of());
    }

    public static ParamDef date(String name, String label, String sqlFragment) {
        return new ParamDef(name, label, ParamType.DATE, sqlFragment, 0, 0, null, List.of(), Map.of());
    }

    public static ParamDef integer(String name, String label, int min, int max, String sqlFragment) {
        return new ParamDef(name, label, ParamType.INTEGER, sqlFragment, min, max, null, List.of(), Map.of());
    }

    /** A top-N limit applied as FETCH FIRST ? ROWS ONLY; always has a value (the default if absent). */
    public static ParamDef rowLimit(String name, String label, int min, int max, int defaultValue) {
        return new ParamDef(name, label, ParamType.ROW_LIMIT, null, min, max,
                String.valueOf(defaultValue), List.of(), Map.of());
    }

    public static ParamDef state(String name, String label, String sqlFragment) {
        return new ParamDef(name, label, ParamType.STATE, sqlFragment, 0, 0, null, BrazilStates.CODES, Map.of());
    }

    public static ParamDef choice(String name, String label, List<String> choices, String sqlFragment) {
        return new ParamDef(name, label, ParamType.CHOICE, sqlFragment, 0, 0, null, choices, Map.of());
    }

    /** The allowed categories are not known in code; they are read from the warehouse at startup. */
    public static ParamDef category(String name, String label, String sqlFragment) {
        return new ParamDef(name, label, ParamType.CATEGORY, sqlFragment, 0, 0, null, List.of(), Map.of());
    }

    /** A checkbox. When ticked, sqlFragment (which has no "?") is added to the WHERE clause. */
    public static ParamDef flag(String name, String label, String sqlFragment) {
        return new ParamDef(name, label, ParamType.FLAG, sqlFragment, 0, 0, null, List.of(), Map.of());
    }

    /** Picks one column from a whitelist; always has a value (the default if absent). */
    public static ParamDef column(String name, String label, Map<String, String> options, String defaultOption) {
        return new ParamDef(name, label, ParamType.COLUMN, null, 0, 0, defaultOption, List.of(), options);
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public ParamType getType() {
        return type;
    }

    /** Constant SQL added to the WHERE clause, or null for ROW_LIMIT and COLUMN. */
    public String getSqlFragment() {
        return sqlFragment;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    /** Raw default used when the parameter is absent, or null for "no filter". */
    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getChoices() {
        return choices;
    }

    public Map<String, String> getColumnOptions() {
        return columnOptions;
    }
}
