package com.reportingportal.report;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server-side validation of report parameters: every type's rule and its exact message. */
class ParamValidatorTest {

    private static final List<String> CATEGORIES = List.of("health_beauty", "watches_gifts");

    private static ValidationResult validate(ReportDefinition report, String... nameValuePairs) {
        Map<String, String[]> input = new HashMap<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            String name = nameValuePairs[i];
            String[] old = input.get(name);
            if (old == null) {
                input.put(name, new String[] {nameValuePairs[i + 1]});
            } else {                                          // same name twice
                input.put(name, new String[] {old[0], nameValuePairs[i + 1]});
            }
        }
        return ParamValidator.validate(report, input, CATEGORIES);
    }

    private static String error(ValidationResult result, String field) {
        assertFalse(result.isValid(), "expected a validation error on " + field);
        return result.getErrors().get(field);
    }

    // ---- MONTH ----

    @Test
    void validMonthRangeIsBoundAsFirstDayOfMonth() {
        ValidationResult result = validate(ReportRegistry.monthlyRevenue(), "from_month", "2017-01", "to_month", "2017-12");
        assertTrue(result.isValid());
        assertEquals(Date.valueOf("2017-01-01"), result.getParams().value("from_month"));
        assertEquals(Date.valueOf("2017-12-01"), result.getParams().value("to_month"));
    }

    @Test
    void monthOutsideWarehouseRangeOrBadlyWrittenIsRejected() {
        String expected = "From month must be a month between 2016-01 and 2019-12, written YYYY-MM.";
        for (String bad : List.of("2017-13", "2017-00", "2015-12", "2020-01", "2017-1", "17-01", "abc", "2017-01-01")) {
            assertEquals(expected, error(validate(ReportRegistry.monthlyRevenue(), "from_month", bad), "from_month"), bad);
        }
    }

    @Test
    void fromAfterToIsRejectedOnTheToField() {
        ValidationResult result = validate(ReportRegistry.monthlyRevenue(), "from_month", "2018-06", "to_month", "2017-01");
        assertEquals("To month must not be before From month.", error(result, "to_month"));
    }

    // ---- DATE ----

    @Test
    void impossibleDateIsRejectedButLeapDayIsAccepted() {
        assertEquals("Order date from must be a real date between 2016-01-01 and 2019-12-31, written YYYY-MM-DD.",
                error(validate(ReportRegistry.repeatPurchaseGap(), "from_date", "2017-02-30"), "from_date"));
        assertTrue(validate(ReportRegistry.repeatPurchaseGap(), "from_date", "2016-02-29").isValid());
        assertFalse(validate(ReportRegistry.repeatPurchaseGap(), "from_date", "2017-02-29").isValid());
    }

    // ---- STATE ----

    @Test
    void stateIsUpperCasedAndMustBeABrazilianState() {
        ValidationResult ok = validate(ReportRegistry.lateDelivery(), "state", " sp ");
        assertTrue(ok.isValid());
        assertEquals("SP", ok.getParams().value("state"));

        for (String bad : List.of("XX", "S", "SPP", "SP'--", "1A")) {
            assertEquals("Customer state must be a Brazilian state code such as SP or RJ.",
                    error(validate(ReportRegistry.lateDelivery(), "state", bad), "state"), bad);
        }
    }

    // ---- INTEGER / ROW_LIMIT ----

    @Test
    void wholeNumbersMustBeInRange() {
        String expected = "Top N (1 to 10) must be a whole number from 1 to 10.";
        for (String bad : List.of("0", "11", "-1", "1.5", "1e1", "abc", "99999999999")) {
            assertEquals(expected, error(validate(ReportRegistry.sellerRank(), "top_n", bad), "top_n"), bad);
        }
        assertEquals(10, validate(ReportRegistry.sellerRank(), "top_n", "10").getParams().value("top_n"));
    }

    @Test
    void rowLimitAndColumnParametersGetTheirDefaults() {
        ValidationResult result = validate(ReportRegistry.categoryQuarters());
        assertTrue(result.isValid());
        assertEquals(10, result.getParams().value("top_n"));
        assertEquals("rev_2018_q2", result.getParams().column("quarter"));
    }

    // ---- CHOICE / CATEGORY / FLAG / COLUMN ----

    @Test
    void choiceMustBeOneOfTheListedValues() {
        assertTrue(validate(ReportRegistry.revenueGroupingSets(), "breakdown", "PAYMENT TYPE").isValid());
        assertEquals("Breakdown must be one of: YEAR, PAYMENT TYPE, TOTAL.",
                error(validate(ReportRegistry.revenueGroupingSets(), "breakdown", "year"), "breakdown"));
    }

    @Test
    void categoryMustBeAKnownCategory() {
        assertTrue(validate(ReportRegistry.revenueStateCategory(), "category", "health_beauty").isValid());
        assertEquals("Category is not a known product category.",
                error(validate(ReportRegistry.revenueStateCategory(), "category", "x' OR '1'='1"), "category"));
    }

    @Test
    void flagIsOnOrAbsent() {
        assertEquals(Boolean.TRUE,
                validate(ReportRegistry.lateDelivery(), "hide_subtotals", "on").getParams().value("hide_subtotals"));
        assertNull(validate(ReportRegistry.lateDelivery()).getParams().value("hide_subtotals"));
        assertEquals("Hide subtotal rows must be on or off.",
                error(validate(ReportRegistry.lateDelivery(), "hide_subtotals", "yes"), "hide_subtotals"));
    }

    @Test
    void columnChoiceMapsToTheWhitelistedColumnName() {
        assertEquals("rev_2017_q4",
                validate(ReportRegistry.categoryQuarters(), "quarter", "2017_Q4").getParams().column("quarter"));
        assertTrue(error(validate(ReportRegistry.categoryQuarters(), "quarter", "rev_2018_q1 DESC, (SELECT 1 FROM dual)"),
                "quarter").startsWith("Rank by quarter must be one of: 2017_Q1"));
    }

    // ---- sort / dir / page ----

    @Test
    void sortMustBeOnTheWhitelist() {
        String expected = "Sort column must be one of: month_start, revenue, order_count, avg_order_value, mom_growth_pct.";
        for (String bad : List.of("revenue;DROP TABLE users--", "password_hash", "ytd_revenue", "REVENUE", "1")) {
            assertEquals(expected, error(validate(ReportRegistry.monthlyRevenue(), "sort", bad), "sort"), bad);
        }
    }

    @Test
    void sortDirectionAndPageHaveDefaultsAndLimits() {
        ValidatedParams params = validate(ReportRegistry.monthlyRevenue()).getParams();
        assertEquals("month_start", params.getSortColumn());
        assertEquals(SortDirection.ASC, params.getDirection());
        assertEquals(1, params.getPage());

        assertEquals("Direction must be asc or desc.", error(validate(ReportRegistry.monthlyRevenue(), "dir", "sideways"), "dir"));
        assertEquals("Page must be a whole number from 1 to 10000.", error(validate(ReportRegistry.monthlyRevenue(), "page", "0"), "page"));
    }

    // ---- general rules ----

    @Test
    void blankMeansAbsentAndUnknownParametersAreIgnored() {
        ValidationResult result = validate(ReportRegistry.monthlyRevenue(), "from_month", "   ", "evil", "1;DROP TABLE x");
        assertTrue(result.isValid());
        assertNull(result.getParams().value("from_month"));
        assertNull(result.getParams().value("evil"));
    }

    @Test
    void duplicateAndOverlongValuesAreRejected() {
        assertEquals("From month was given more than once.",
                error(validate(ReportRegistry.monthlyRevenue(), "from_month", "2017-01", "from_month", "2017-02"), "from_month"));
        assertEquals("From month is too long.",
                error(validate(ReportRegistry.monthlyRevenue(), "from_month", "9".repeat(101)), "from_month"));
    }

    @Test
    void allErrorsAreCollectedNotJustTheFirst() {
        ValidationResult result = validate(ReportRegistry.sellerRank(),
                "seller_state", "XX", "top_n", "11", "sort", "nope", "dir", "up", "page", "0");
        assertEquals(5, result.getErrors().size());
    }

    @Test
    void canonicalQueryStringKeepsDefinitionOrderAndOmitsThePage() {
        ValidatedParams params = validate(ReportRegistry.monthlyRevenue(),
                "page", "3", "dir", "desc", "sort", "revenue", "to_month", "2017-12", "from_month", "2017-01").getParams();
        assertEquals("from_month=2017-01&to_month=2017-12&sort=revenue&dir=desc", params.toQueryString());
    }
}
