package com.reportingportal.report;

/** One column of a report's result: the view column name, its label on screen, and its type. */
public final class ColumnDef {

    private final String name;
    private final String label;
    private final ColumnType type;

    public ColumnDef(String name, String label, ColumnType type) {
        this.name = name;
        this.label = label;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public ColumnType getType() {
        return type;
    }
}
