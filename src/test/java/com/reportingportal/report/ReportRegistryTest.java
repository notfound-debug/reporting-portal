package com.reportingportal.report;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every report definition is internally consistent, and matches the seed data. */
class ReportRegistryTest {

    private static final Pattern VIEW_NAME = Pattern.compile("^v_rpt_[a-z_]+$");
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Set<String> RESERVED = Set.of(ParamValidator.SORT, ParamValidator.DIRECTION, ParamValidator.PAGE);

    @Test
    void thereAreTwelveReportsWithUniqueCodes() {
        List<ReportDefinition> reports = ReportRegistry.definitions();
        assertEquals(12, reports.size());
        Set<String> codes = new HashSet<>();
        for (ReportDefinition report : reports) {
            assertTrue(codes.add(report.getCode()), "duplicate code " + report.getCode());
        }
    }

    @Test
    void identifiersUsedInSqlAreSafeConstants() {
        for (ReportDefinition report : ReportRegistry.definitions()) {
            assertTrue(VIEW_NAME.matcher(report.getView()).matches(), report.getView());
            for (ColumnDef column : report.getColumns()) {
                assertTrue(IDENTIFIER.matcher(column.getName()).matches(), column.getName());
            }
        }
    }

    @Test
    void sortTieBreakerAndChartColumnsAreRealColumns() {
        for (ReportDefinition report : ReportRegistry.definitions()) {
            String code = report.getCode();
            report.getSortableColumns().values().forEach(c -> assertNotNull(report.column(c), code + " sort " + c));
            report.getTieBreakers().forEach(c -> assertNotNull(report.column(c), code + " tie-breaker " + c));
            if (report.isSortable()) {
                assertTrue(report.getSortableColumns().containsKey(report.getDefaultSort()), code + " default sort");
            } else {
                ParamDef orderBy = report.param(report.getOrderByParam());
                assertNotNull(orderBy, code + " has neither a sort whitelist nor an order-by parameter");
                orderBy.getColumnOptions().values().forEach(c -> assertNotNull(report.column(c), code + " option " + c));
            }
            if (report.getChart() != null) {
                assertNotNull(report.column(report.getChart().getLabelColumn()), code + " chart label");
                report.getChart().getSeriesColumns().forEach(c -> assertNotNull(report.column(c), code + " series " + c));
            }
        }
    }

    @Test
    void whereFragmentsHaveOnePlaceholderExceptFlags() {
        for (ReportDefinition report : ReportRegistry.definitions()) {
            Set<String> names = new HashSet<>();
            for (ParamDef param : report.getParams()) {
                String where = report.getCode() + "." + param.getName();
                assertTrue(names.add(param.getName()), "duplicate parameter " + where);
                assertFalse(RESERVED.contains(param.getName()), "reserved name " + where);
                String fragment = param.getSqlFragment();
                long placeholders = fragment == null ? 0 : fragment.chars().filter(c -> c == '?').count();
                switch (param.getType()) {
                    case FLAG -> assertEquals(0, placeholders, where);
                    case COLUMN, ROW_LIMIT -> assertEquals(null, fragment, where);
                    default -> assertEquals(1, placeholders, where);
                }
            }
        }
    }

    @Test
    void exactlyThreeReportsHaveCharts() {
        long charts = ReportRegistry.definitions().stream().filter(ReportDefinition::isHasChart).count();
        assertEquals(3, charts);
    }

    @Test
    void javaCodesMatchTheSeedFileAndEveryReportHasARole() throws IOException {
        Map<String, Set<String>> seeded = SeedFile.rolesByReport();
        Set<String> javaCodes = new HashSet<>();
        ReportRegistry.definitions().forEach(r -> javaCodes.add(r.getCode()));
        assertEquals(javaCodes, seeded.keySet());
        seeded.forEach((code, roles) -> assertFalse(roles.isEmpty(), code + " has no roles"));
    }

    @Test
    void registryRefusesCodesThatDifferFromTheDatabase() throws IOException {
        Map<String, Set<String>> extra = new HashMap<>(SeedFile.rolesByReport());
        extra.put("ghost_report", Set.of("ADMIN"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ReportRegistry(ReportRegistry.definitions(), extra));
        assertTrue(e.getMessage().contains("In the reports table but not defined in Java: [ghost_report]"), e.getMessage());

        Map<String, Set<String>> missing = new HashMap<>(SeedFile.rolesByReport());
        missing.remove("customer_moves");
        e = assertThrows(IllegalStateException.class, () -> new ReportRegistry(ReportRegistry.definitions(), missing));
        assertTrue(e.getMessage().contains("Defined in Java but not in the reports table: [customer_moves]"), e.getMessage());
    }

    @Test
    void registryRefusesAReportThatNobodyCouldSee() throws IOException {
        Map<String, Set<String>> noRoles = new HashMap<>(SeedFile.rolesByReport());
        noRoles.put("weekday_orders", Set.of());
        assertThrows(IllegalStateException.class, () -> new ReportRegistry(ReportRegistry.definitions(), noRoles));
    }
}
