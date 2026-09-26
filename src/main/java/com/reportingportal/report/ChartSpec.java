package com.reportingportal.report;

import java.util.List;

/**
 * How a report is drawn as a Chart.js chart: the chart type, the column used
 * for the x-axis labels, and the columns drawn as data series.
 *
 * The series are either a fixed list of columns, or the column the user picked
 * in a COLUMN parameter (seriesParam), e.g. the quarter in category_quarters.
 */
public final class ChartSpec {

    public enum Type { LINE, BAR, STACKED_BAR }

    private final Type type;
    private final String labelColumn;
    private final List<String> seriesColumns;
    private final String seriesParam;

    private ChartSpec(Type type, String labelColumn, List<String> seriesColumns, String seriesParam) {
        this.type = type;
        this.labelColumn = labelColumn;
        this.seriesColumns = List.copyOf(seriesColumns);
        this.seriesParam = seriesParam;
    }

    public static ChartSpec of(Type type, String labelColumn, String... seriesColumns) {
        return new ChartSpec(type, labelColumn, List.of(seriesColumns), null);
    }

    public static ChartSpec seriesFromParam(Type type, String labelColumn, String columnParam) {
        return new ChartSpec(type, labelColumn, List.of(), columnParam);
    }

    public Type getType() {
        return type;
    }

    public String getLabelColumn() {
        return labelColumn;
    }

    public List<String> getSeriesColumns() {
        return seriesColumns;
    }

    /** Name of the COLUMN parameter that picks the series, or null for fixed series. */
    public String getSeriesParam() {
        return seriesParam;
    }
}
