package com.reportingportal.report;

import com.reportingportal.auth.AuthenticatedUser;

import java.util.Set;

/**
 * The single rule for who may see a report: the user must hold at least one of
 * the roles assigned to the report in report_roles.
 *
 * Every place that shows report data calls this: the report list, the report
 * page, the chart page, the JSON endpoint, schedule creation, the scheduler at
 * run time, and the export download. Hiding a report from the menu is only a
 * convenience; these server-side checks are the actual protection.
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    public static boolean canView(AuthenticatedUser user, ReportDefinition report) {
        return user != null && canView(user.getRoles(), report);
    }

    /** Takes plain role codes, so the scheduler can check a schedule owner's current roles too. */
    public static boolean canView(Set<String> userRoles, ReportDefinition report) {
        for (String role : userRoles) {
            if (report.getRoles().contains(role)) {
                return true;
            }
        }
        return false;
    }
}
