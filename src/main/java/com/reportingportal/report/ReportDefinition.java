package com.reportingportal.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the portal knows about one report: which view it reads, which
 * columns it shows, which parameters its form has and how each reaches the SQL,
 * which columns may be sorted on (the whitelist), and who may see it.
 *
 * Definitions are Java code (see ReportRegistry) because they contain SQL
 * fragments and identifiers; if they were rows in a table, anyone able to
 * UPDATE that table could change the SQL. Only the roles come from the database.
 * Immutable; build with {@link #builder(String)}.
 */
public final class ReportDefinition {

    /** A "from" parameter must not be after its "to" parameter. */
    public record RangeRule(String fromParam, String toParam) {
    }

    private final String code;
    private final String title;
    private final String question;
    private final String view;
    private final List<ColumnDef> columns;
    private final List<ParamDef> params;
    private final List<RangeRule> rangeRules;
    /** Sort key accepted in the URL -> column name used in ORDER BY. Keys equal column names. */
    private final Map<String, String> sortableColumns;
    private final String defaultSort;
    private final SortDirection defaultDirection;
    /** For reports without a user sort: the COLUMN parameter whose column orders the rows (descending). */
    private final String orderByParam;
    private final List<String> tieBreakers;
    private final boolean paged;
    private final ChartSpec chart;
    private final Set<String> roles;
    /** Kept so {@link #withRoles} can make a copy with different roles. */
    private final Builder builder;

    private ReportDefinition(Builder b, Set<String> roles) {
        this.code = b.code;
        this.title = b.title;
        this.question = b.question;
        this.view = b.view;
        this.columns = List.copyOf(b.columns);
        this.params = List.copyOf(b.params);
        this.rangeRules = List.copyOf(b.rangeRules);
        this.sortableColumns = new LinkedHashMap<>(b.sortableColumns);
        this.defaultSort = b.defaultSort;
        this.defaultDirection = b.defaultDirection;
        this.orderByParam = b.orderByParam;
        this.tieBreakers = List.copyOf(b.tieBreakers);
        this.paged = b.paged;
        this.chart = b.chart;
        this.roles = Set.copyOf(roles);
        this.builder = b;
    }

    public static Builder builder(String code) {
        return new Builder(code);
    }

    /** The same definition with the roles read from report_roles at startup. */
    public ReportDefinition withRoles(Set<String> newRoles) {
        return new ReportDefinition(builder, newRoles);
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getQuestion() {
        return question;
    }

    public String getView() {
        return view;
    }

    public List<ColumnDef> getColumns() {
        return columns;
    }

    public List<ParamDef> getParams() {
        return params;
    }

    public List<RangeRule> getRangeRules() {
        return rangeRules;
    }

    public Map<String, String> getSortableColumns() {
        return sortableColumns;
    }

    public boolean isSortable() {
        return !sortableColumns.isEmpty();
    }

    public String getDefaultSort() {
        return defaultSort;
    }

    public SortDirection getDefaultDirection() {
        return defaultDirection;
    }

    public String getOrderByParam() {
        return orderByParam;
    }

    public List<String> getTieBreakers() {
        return tieBreakers;
    }

    public boolean isPaged() {
        return paged;
    }

    /** The chart, or null if this report has no chart page. */
    public ChartSpec getChart() {
        return chart;
    }

    public boolean isHasChart() {
        return chart != null;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public ParamDef param(String name) {
        for (ParamDef param : params) {
            if (param.getName().equals(name)) {
                return param;
            }
        }
        return null;
    }

    public ColumnDef column(String name) {
        for (ColumnDef column : columns) {
            if (column.getName().equals(name)) {
                return column;
            }
        }
        return null;
    }

    /** Collects a definition step by step, so the 12 definitions in ReportRegistry read like a spec. */
    public static final class Builder {
        private final String code;
        private String title;
        private String question;
        private String view;
        private final List<ColumnDef> columns = new ArrayList<>();
        private final List<ParamDef> params = new ArrayList<>();
        private final List<RangeRule> rangeRules = new ArrayList<>();
        private final Map<String, String> sortableColumns = new LinkedHashMap<>();
        private String defaultSort;
        private SortDirection defaultDirection = SortDirection.ASC;
        private String orderByParam;
        private final List<String> tieBreakers = new ArrayList<>();
        private boolean paged = true;
        private ChartSpec chart;

        private Builder(String code) {
            this.code = code;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder question(String value) {
            this.question = value;
            return this;
        }

        public Builder view(String value) {
            this.view = value;
            return this;
        }

        public Builder column(String name, String label, ColumnType type) {
            columns.add(new ColumnDef(name, label, type));
            return this;
        }

        public Builder param(ParamDef param) {
            params.add(param);
            return this;
        }

        public Builder rangeRule(String fromParam, String toParam) {
            rangeRules.add(new RangeRule(fromParam, toParam));
            return this;
        }

        /** The sort whitelist: only these columns can be used in ORDER BY. */
        public Builder sortable(String... columnNames) {
            for (String columnName : columnNames) {
                sortableColumns.put(columnName, columnName);
            }
            return this;
        }

        public Builder defaultSort(String columnName, SortDirection direction) {
            this.defaultSort = columnName;
            this.defaultDirection = direction;
            return this;
        }

        /** Instead of a user sort: order by the column chosen in this COLUMN parameter, largest first. */
        public Builder orderByColumnParam(String paramName) {
            this.orderByParam = paramName;
            return this;
        }

        /** Extra ORDER BY columns that make the order unique, so pages never overlap or skip rows. */
        public Builder tieBreaker(String... columnNames) {
            tieBreakers.addAll(List.of(columnNames));
            return this;
        }

        public Builder notPaged() {
            this.paged = false;
            return this;
        }

        public Builder chart(ChartSpec value) {
            this.chart = value;
            return this;
        }

        public ReportDefinition build() {
            return new ReportDefinition(this, Set.of());
        }
    }
}
