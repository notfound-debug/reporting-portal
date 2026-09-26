package com.reportingportal.export;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NextRunCalculatorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void laterTodayWhenTheTimeHasNotPassed() {
        Instant now = Instant.parse("2026-09-26T05:00:00Z");
        assertEquals(Instant.parse("2026-09-26T06:00:00Z"), NextRunCalculator.nextRun(LocalTime.of(6, 0), now, UTC));
    }

    @Test
    void tomorrowWhenTheTimeHasPassed() {
        Instant now = Instant.parse("2026-09-26T07:00:00Z");
        assertEquals(Instant.parse("2026-09-27T06:00:00Z"), NextRunCalculator.nextRun(LocalTime.of(6, 0), now, UTC));
    }

    @Test
    void exactlyNowCountsAsPassed() {
        Instant now = Instant.parse("2026-09-26T06:00:00Z");
        assertEquals(Instant.parse("2026-09-27T06:00:00Z"), NextRunCalculator.nextRun(LocalTime.of(6, 0), now, UTC));
    }

    @Test
    void timeOfDayIsInThePortalTimeZone() {
        // 06:00 in India (UTC+05:30) is 00:30 UTC.
        Instant now = Instant.parse("2026-09-26T00:00:00Z");
        assertEquals(Instant.parse("2026-09-26T00:30:00Z"),
                NextRunCalculator.nextRun(LocalTime.of(6, 0), now, ZoneId.of("Asia/Kolkata")));
    }

    @Test
    void timeThatDoesNotExistOnADaylightSavingDayMovesPastTheGap() {
        // Brazil started DST at midnight on 2018-11-04: clocks jumped from 00:00 to 01:00,
        // so 00:30 did not exist that day. The run happens at 01:30 local time instead.
        ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");
        Instant now = ZonedDateTime.of(2018, 11, 3, 12, 0, 0, 0, saoPaulo).toInstant();
        ZonedDateTime next = NextRunCalculator.nextRun(LocalTime.of(0, 30), now, saoPaulo).atZone(saoPaulo);
        assertEquals("2018-11-04T01:30-02:00[America/Sao_Paulo]", next.toString());
    }
}
