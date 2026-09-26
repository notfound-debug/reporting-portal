package com.reportingportal.report;

import com.reportingportal.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;

import java.util.logging.Logger;

/**
 * Steps shared by every servlet that serves a report (the HTML page, the chart
 * page and the JSON endpoint), so all three find the report and apply the
 * access rule in exactly the same way.
 */
final class ReportRequests {

    private static final Logger LOG = Logger.getLogger(ReportRequests.class.getName());

    private ReportRequests() {
    }

    /** The report for a path info like "/monthly_revenue", or null if there is none. */
    static ReportDefinition findReport(ReportRegistry registry, String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        return registry.find(pathInfo.substring(1)).orElse(null);
    }

    static AuthenticatedUser currentUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
    }

    /** Every refused attempt is logged, whichever page or endpoint it came through. */
    static void logDenied(AuthenticatedUser user, ReportDefinition report, String via) {
        LOG.warning("Access denied: user=" + user.getUsername() + " report=" + report.getCode() + " via=" + via);
    }
}
