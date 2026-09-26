package com.reportingportal.report;

import com.reportingportal.auth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Role checks, using the real report definitions and the access rules from db/02_seed.sql. */
class AccessPolicyTest {

    private static ReportRegistry registry;

    private static final AuthenticatedUser VIEWER = new AuthenticatedUser(1, "viewer", "Viewer", Set.of("VIEWER"));
    private static final AuthenticatedUser ANALYST = new AuthenticatedUser(2, "analyst", "Analyst", Set.of("ANALYST"));
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(3, "admin", "Admin", Set.of("ADMIN"));
    private static final AuthenticatedUser NO_ROLES = new AuthenticatedUser(4, "nobody", "Nobody", Set.of());

    @BeforeAll
    static void loadRegistry() throws IOException {
        registry = SeedFile.registry();
    }

    private static boolean canView(AuthenticatedUser user, String code) {
        return AccessPolicy.canView(user, registry.find(code).orElseThrow());
    }

    @Test
    void eachRoleSeesTheAgreedNumberOfReports() {
        assertEquals(4, registry.visibleTo(VIEWER).size());
        assertEquals(11, registry.visibleTo(ANALYST).size());
        assertEquals(12, registry.visibleTo(ADMIN).size());
        assertEquals(0, registry.visibleTo(NO_ROLES).size());
    }

    @Test
    void viewerSeesOnlyCompanyLevelSummaries() {
        List<String> visible = registry.visibleTo(VIEWER).stream().map(ReportDefinition::getCode).toList();
        assertEquals(List.of("monthly_revenue", "revenue_grouping_sets", "weekday_orders", "late_delivery"), visible);
        assertFalse(canView(VIEWER, "payment_mix"));
        assertFalse(canView(VIEWER, "category_quarters"));
        assertFalse(canView(VIEWER, "customer_moves"));
    }

    @Test
    void onlyAdminSeesCustomerAddressHistory() {
        assertFalse(canView(VIEWER, "customer_moves"));
        assertFalse(canView(ANALYST, "customer_moves"));
        assertTrue(canView(ADMIN, "customer_moves"));
    }

    @Test
    void analystSeesDetailReports() {
        assertTrue(canView(ANALYST, "payment_mix"));
        assertTrue(canView(ANALYST, "repeat_purchase_gap"));
        assertTrue(canView(ANALYST, "monthly_revenue"));
    }

    @Test
    void anyOneMatchingRoleIsEnough() {
        AuthenticatedUser both = new AuthenticatedUser(5, "both", "Both", Set.of("VIEWER", "ANALYST"));
        assertTrue(canView(both, "payment_mix"));
        assertEquals(11, registry.visibleTo(both).size());
    }

    @Test
    void noUserMeansNoAccess() {
        assertFalse(AccessPolicy.canView((AuthenticatedUser) null, registry.find("monthly_revenue").orElseThrow()));
    }

    @Test
    void schedulerCheckWithPlainRoleCodesGivesTheSameAnswer() {
        ReportDefinition payment = registry.find("payment_mix").orElseThrow();
        assertFalse(AccessPolicy.canView(Set.of("VIEWER"), payment));
        assertTrue(AccessPolicy.canView(Set.of("ANALYST"), payment));
    }
}
