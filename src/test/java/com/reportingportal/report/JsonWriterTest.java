package com.reportingportal.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonWriterTest {

    @Test
    void writesObjectsArraysAndPlainValues() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report", "monthly_revenue");
        body.put("rows", List.of(Arrays.asList(YearMonth.of(2017, 1), 787, new BigDecimal("120098.27"), null)));
        body.put("truncated", false);
        assertEquals("{\"report\":\"monthly_revenue\",\"rows\":[[\"2017-01\",787,120098.27,null]],\"truncated\":false}",
                JsonWriter.toJson(body));
    }

    @Test
    void bigDecimalsAreNeverInScientificNotation() {
        assertEquals("1000", JsonWriter.toJson(new BigDecimal("1E+3")));
        assertEquals("0.00001", JsonWriter.toJson(new BigDecimal("0.00001")));
    }

    @Test
    void datesAreIsoStrings() {
        assertEquals("\"2017-06-01\"", JsonWriter.toJson(LocalDate.of(2017, 6, 1)));
    }

    @Test
    void quotesBackslashesAndControlCharactersAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"", JsonWriter.toJson("a\"b\\c\nd\te\u0001"));
    }

    @Test
    void htmlSignificantCharactersAreEscapedSoJsonCannotEndAScriptBlock() {
        assertEquals("\"\\u003c/script\\u003e\\u003cb\\u003e \\u0026amp;\"", JsonWriter.toJson("</script><b> &amp;"));
    }

    @Test
    void unsupportedTypesAreRefusedRatherThanGuessed() {
        assertThrows(IllegalArgumentException.class, () -> JsonWriter.toJson(new Object()));
    }
}
