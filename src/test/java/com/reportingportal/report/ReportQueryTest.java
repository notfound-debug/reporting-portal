package com.reportingportal.report;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The SQL text comes only from constants; user values only ever appear in the bind list. */
class ReportQueryTest {

    private static final List<String> CATEGORIES = List.of("health_beauty");

    private static ValidatedParams params(ReportDefinition report, String... nameValuePairs) {
        Map<String, String[]> input = new HashMap<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            input.put(nameValuePairs[i], new String[] {nameValuePairs[i + 1]});
        }
        ValidationResult result = ParamValidator.validate(report, input, CATEGORIES);
        assertTrue(result.isValid(), () -> "unexpected errors " + result.getErrors());
        return result.getParams();
    }

    @Test
    void designDocumentExampleProducesExactlyThisSqlAndTheseBinds() {
        ReportDefinition report = ReportRegistry.repeatPurchaseGap();
        ReportQuery query = ReportQuery.forPage(report, params(report,
                "from_date", "2017-06-01", "min_gap_days", "30", "sort", "days_since_previous", "dir", "desc", "page", "2"));

        assertEquals("SELECT customer_unique_id, order_number, total_orders, order_id, order_date, previous_order_date, "
                + "days_since_previous, next_order_date FROM v_rpt_repeat_purchase_gap "
                + "WHERE order_date >= ? AND days_since_previous >= ? "
                + "ORDER BY days_since_previous DESC NULLS LAST, order_id ASC "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", query.sql());
        assertEquals(List.of(Date.valueOf("2017-06-01"), 30, 50, 51), query.binds());
    }

    @Test
    void absentParametersAddNoCondition() {
        ReportDefinition report = ReportRegistry.monthlyRevenue();
        ReportQuery query = ReportQuery.forPage(report, params(report));
        assertFalse(query.sql().contains("WHERE"));
        assertEquals(List.of(0, 51), query.binds());
    }

    @Test
    void flagAddsAConstantConditionWithoutABind() {
        ReportDefinition report = ReportRegistry.lateDelivery();
        ReportQuery query = ReportQuery.forPage(report, params(report, "hide_subtotals", "on", "state", "SP"));
        assertTrue(query.sql().contains("WHERE customer_state = ? AND grouping_level = 0 ORDER BY"));
        assertEquals(List.of("SP", 0, 51), query.binds());
    }

    @Test
    void userValuesNeverAppearInTheSqlText() {
        ReportDefinition report = ReportRegistry.revenueStateCategory();
        ReportQuery query = ReportQuery.forPage(report, params(report, "state", "RJ", "category", "health_beauty"));
        assertFalse(query.sql().contains("RJ"));
        assertFalse(query.sql().contains("health_beauty"));
        assertEquals(List.of("RJ", "health_beauty", 0, 51), query.binds());
    }

    @Test
    void sortUsesTheWhitelistedColumnAndPagingSkipsWholePages() {
        ReportDefinition report = ReportRegistry.monthlyRevenue();
        ReportQuery query = ReportQuery.forPage(report, params(report, "sort", "revenue", "dir", "desc", "page", "3"));
        assertTrue(query.sql().endsWith("ORDER BY revenue DESC NULLS LAST, month_start ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertEquals(List.of(100, 51), query.binds());
    }

    @Test
    void chosenColumnOrdersTheRowsAndTopNBecomesFetchFirst() {
        ReportDefinition report = ReportRegistry.categoryQuarters();
        ReportQuery query = ReportQuery.forPage(report, params(report, "quarter", "2017_Q4", "top_n", "5"));
        assertTrue(query.sql().endsWith("ORDER BY rev_2017_q4 DESC NULLS LAST, category ASC FETCH FIRST ? ROWS ONLY"));
        assertEquals(List.of(5), query.binds());
    }

    @Test
    void chartAndExportQueriesAskForOneRowMoreThanTheCap() {
        ReportDefinition report = ReportRegistry.cohortRetention();
        ReportQuery query = ReportQuery.forAllRows(report, params(report, "max_months", "3"), 1000);
        assertTrue(query.sql().endsWith("FETCH FIRST ? ROWS ONLY"));
        assertEquals(List.of(3, 1001), query.binds());
    }

    /** For every report, with every parameter set: one bind value per "?", in both query shapes. */
    @Test
    void everyReportHasOneBindPerPlaceholder() {
        for (ReportDefinition report : ReportRegistry.definitions()) {
            ValidatedParams all = params(report, sampleValues(report));
            for (ReportQuery query : List.of(ReportQuery.forPage(report, all), ReportQuery.forAllRows(report, all, 100))) {
                long placeholders = query.sql().chars().filter(c -> c == '?').count();
                assertEquals(placeholders, query.binds().size(), report.getCode() + ": " + query.sql());
            }
        }
    }

    /** A valid value for every parameter of the report. */
    private static String[] sampleValues(ReportDefinition report) {
        List<String> pairs = new java.util.ArrayList<>();
        for (ParamDef param : report.getParams()) {
            pairs.add(param.getName());
            pairs.add(switch (param.getType()) {
                case MONTH -> "2017-01";
                case DATE -> "2017-01-01";
                case INTEGER, ROW_LIMIT -> String.valueOf(param.getMax());
                case STATE -> "SP";
                case CHOICE -> param.getChoices().get(0);
                case CATEGORY -> "health_beauty";
                case FLAG -> "on";
                case COLUMN -> param.getColumnOptions().keySet().iterator().next();
            });
        }
        return pairs.toArray(new String[0]);
    }
}
