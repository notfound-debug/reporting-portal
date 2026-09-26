package com.reportingportal.export;

import com.reportingportal.report.ParamValidator;
import com.reportingportal.report.ReportDefinition;
import com.reportingportal.report.ReportRegistry;
import com.reportingportal.report.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A schedule stores the canonical query string; parsing it back must give the same parameters. */
class QueryStringsTest {

    private static ReportDefinition report(String code) {
        return ReportRegistry.definitions().stream().filter(r -> r.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void decodesEncodedValuesAndRepeatedNames() {
        Map<String, String[]> parsed = QueryStrings.parse("breakdown=PAYMENT+TYPE&x=a%26b&x=2&flag");
        assertArrayEquals(new String[] {"PAYMENT TYPE"}, parsed.get("breakdown"));
        assertArrayEquals(new String[] {"a&b", "2"}, parsed.get("x"));
        assertArrayEquals(new String[] {""}, parsed.get("flag"));
    }

    @Test
    void storedParametersSurviveTheRoundTrip() {
        ReportDefinition report = report("revenue_grouping_sets");
        ValidationResult first = ParamValidator.validate(report,
                Map.of("breakdown", new String[] {"PAYMENT TYPE"}, "sort", new String[] {"revenue"}, "dir", new String[] {"desc"}),
                List.of());
        String stored = first.getParams().toQueryString();
        assertEquals("breakdown=PAYMENT+TYPE&sort=revenue&dir=desc", stored);

        ValidationResult again = ParamValidator.validate(report, QueryStrings.parse(stored), List.of());
        assertTrue(again.isValid());
        assertEquals(stored, again.getParams().toQueryString());
    }
}
