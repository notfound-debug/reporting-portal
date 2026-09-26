package com.reportingportal.export;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Works out when a "daily at HH:MM" schedule runs next.
 *
 * The time of day is in the portal's time zone (PORTAL_TIME_ZONE); the result
 * is an Instant, which is stored in the database as UTC. ZonedDateTime deals
 * with daylight-saving changes: on a day when 02:30 does not exist (clocks jump
 * forward), a 02:30 schedule runs at the first valid time after the gap.
 */
public final class NextRunCalculator {

    private NextRunCalculator() {
    }

    /** The first occurrence of runTime strictly after now: later today, or else tomorrow. */
    public static Instant nextRun(LocalTime runTime, Instant now, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        ZonedDateTime candidate = ZonedDateTime.of(today, runTime, zone);
        if (!candidate.toInstant().isAfter(now)) {
            candidate = ZonedDateTime.of(today.plusDays(1), runTime, zone);
        }
        return candidate.toInstant();
    }
}
