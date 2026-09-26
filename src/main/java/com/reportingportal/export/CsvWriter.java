package com.reportingportal.export;

import com.reportingportal.report.ColumnDef;
import com.reportingportal.report.ColumnType;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.List;

/**
 * Writes report rows as CSV (RFC 4180): comma-separated, CRLF line ends, the
 * column names as the header row. A field is put in double quotes when it
 * contains a comma, a quote, a line break, or leading/trailing spaces; a quote
 * inside a field is doubled ("" ).
 *
 * CSV injection: a spreadsheet treats a cell starting with = + - @ (or a tab or
 * carriage return) as a formula, so a text value like =HYPERLINK(...) could run
 * when the file is opened. Text cells that start that way get a leading
 * apostrophe, which makes Excel show them as plain text. Number cells are left
 * alone, because -5.2 is a legitimate negative number.
 */
public final class CsvWriter {

    private static final String LINE_END = "\r\n";

    private CsvWriter() {
    }

    public static void write(List<ColumnDef> columns, List<List<Object>> rows, Writer out) throws IOException {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(quoteIfNeeded(columns.get(i).getName()));
        }
        out.write(LINE_END);

        for (List<Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    out.write(',');
                }
                out.write(cell(row.get(i), columns.get(i).getType()));
            }
            out.write(LINE_END);
        }
    }

    /** One value as CSV text. Dates are ISO (2017-06-01, 2017-06); NULL is an empty field. */
    static String cell(Object value, ColumnType type) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString();          // never 1E+3 notation
        }
        String text = value.toString();
        if (type == ColumnType.TEXT && startsLikeFormula(text)) {
            text = "'" + text;
        }
        return quoteIfNeeded(text);
    }

    private static boolean startsLikeFormula(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    static String quoteIfNeeded(String text) {
        boolean needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n")
                || text.contains("\r") || (!text.isEmpty() && (text.charAt(0) == ' ' || text.charAt(text.length() - 1) == ' '));
        if (!needsQuotes) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
