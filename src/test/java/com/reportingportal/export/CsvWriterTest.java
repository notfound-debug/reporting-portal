package com.reportingportal.export;

import com.reportingportal.report.ColumnDef;
import com.reportingportal.report.ColumnType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    private static final List<ColumnDef> COLUMNS = List.of(
            new ColumnDef("city", "City", ColumnType.TEXT),
            new ColumnDef("month_start", "Month", ColumnType.MONTH),
            new ColumnDef("order_date", "Order date", ColumnType.DATE),
            new ColumnDef("growth_pct", "Growth", ColumnType.PERCENT));

    private static String csv(List<Object> row) throws IOException {
        StringWriter out = new StringWriter();
        CsvWriter.write(COLUMNS, List.of(row), out);
        return out.toString();
    }

    @Test
    void headerIsColumnNamesAndLinesEndWithCrLf() throws IOException {
        assertEquals("city,month_start,order_date,growth_pct\r\nsao paulo,2017-01,2017-01-05,12.5\r\n",
                csv(List.of("sao paulo", YearMonth.of(2017, 1), LocalDate.of(2017, 1, 5), new BigDecimal("12.5"))));
    }

    @Test
    void commasQuotesAndLineBreaksAreQuoted() {
        assertEquals("\"rio de janeiro, rio de janeiro\"", CsvWriter.cell("rio de janeiro, rio de janeiro", ColumnType.TEXT));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.cell("say \"hi\"", ColumnType.TEXT));
        assertEquals("\"two\nlines\"", CsvWriter.cell("two\nlines", ColumnType.TEXT));
        assertEquals("\" padded \"", CsvWriter.cell(" padded ", ColumnType.TEXT));
        assertEquals("plain", CsvWriter.cell("plain", ColumnType.TEXT));
    }

    @Test
    void nullIsAnEmptyField() throws IOException {
        assertEquals("city,month_start,order_date,growth_pct\r\n,,,\r\n", csv(Arrays.asList(null, null, null, null)));
    }

    @Test
    void numbersArePlainNeverScientific() {
        assertEquals("1000", CsvWriter.cell(new BigDecimal("1E+3"), ColumnType.MONEY));
        assertEquals("120098.27", CsvWriter.cell(new BigDecimal("120098.27"), ColumnType.MONEY));
    }

    @Test
    void textThatLooksLikeAFormulaIsDefused() {
        // Defused with a leading apostrophe, then quoted because it contains quotes.
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", CsvWriter.cell("=HYPERLINK(\"http://x\")", ColumnType.TEXT));
        assertEquals("'+1", CsvWriter.cell("+1", ColumnType.TEXT));
        assertEquals("'-2", CsvWriter.cell("-2", ColumnType.TEXT));
        assertEquals("'@SUM(A1)", CsvWriter.cell("@SUM(A1)", ColumnType.TEXT));
    }

    @Test
    void negativeNumbersAreNotTouched() {
        assertEquals("-5.2", CsvWriter.cell(new BigDecimal("-5.2"), ColumnType.PERCENT));
    }
}
