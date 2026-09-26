package com.reportingportal.export;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Generated file names, and the path check that keeps downloads inside the export folder. */
class ExportFilesTest {

    private static final Path EXPORT_DIR = Path.of("/exports");

    @Test
    void fileNameIsReportCodeTimestampAndRunId() {
        ZonedDateTime when = ZonedDateTime.of(2026, 9, 26, 6, 52, 20, 0, ZoneId.of("UTC"));
        assertEquals("repeat_purchase_gap_20260926_065220_run1.csv", ExportFiles.fileName("repeat_purchase_gap", when, 1));
    }

    @Test
    void generatedNameResolvesDirectlyInsideTheExportFolder() {
        Path file = ExportFiles.resolveInside(EXPORT_DIR, "monthly_revenue_20260926_065220_run12.csv");
        assertEquals(EXPORT_DIR.toAbsolutePath().normalize(), file.getParent());
    }

    @Test
    void traversalAbsolutePathsAndOtherNamesAreRefused() {
        for (String bad : List.of(
                "../../etc/passwd",
                "../monthly_revenue_20260926_065220_run1.csv",
                "/etc/passwd",
                "monthly_revenue_20260926_065220_run1.csv/../../x.csv",
                "sub/monthly_revenue_20260926_065220_run1.csv",
                ".tmp-run1.csv",
                "report.csv",
                "")) {
            assertThrows(IllegalArgumentException.class, () -> ExportFiles.resolveInside(EXPORT_DIR, bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> ExportFiles.resolveInside(EXPORT_DIR, null));
    }
}
