package com.reportingportal.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    private static Map<String, String> complete() {
        Map<String, String> env = new HashMap<>();
        env.put("DB_URL", "jdbc:oracle:thin:@//oracle:1521/XEPDB1");
        env.put("PORTAL_DB_USER", "portal");
        env.put("PORTAL_DB_PASSWORD", "secret-portal");
        env.put("WAREHOUSE_DB_USER", "dw_report");
        env.put("WAREHOUSE_DB_PASSWORD", "secret-report");
        return env;
    }

    @Test
    void defaultsApplyWhenOptionalSettingsAreMissing() {
        AppConfig config = AppConfig.from(complete());
        assertEquals(2, config.portalPoolSize());
        assertEquals(5, config.warehousePoolSize());
        assertEquals("DW", config.warehouseSchema());
        assertEquals("UTC", config.timeZone().getId());
        assertEquals(30, config.schedulerIntervalSeconds());
    }

    @Test
    void everyMissingVariableIsNamedAtOnce() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AppConfig.from(Map.of()));
        for (String name : new String[] {"DB_URL", "PORTAL_DB_USER", "PORTAL_DB_PASSWORD", "WAREHOUSE_DB_USER", "WAREHOUSE_DB_PASSWORD"}) {
            assertTrue(e.getMessage().contains("missing environment variable " + name), e.getMessage());
        }
    }

    @Test
    void schemaNameIsCheckedBecauseItIsPastedIntoSql() {
        Map<String, String> env = complete();
        env.put("WAREHOUSE_SCHEMA", "DW; DROP USER portal");
        assertThrows(IllegalStateException.class, () -> AppConfig.from(env));
    }

    @Test
    void badNumbersAndTimeZonesAreRejected() {
        Map<String, String> env = complete();
        env.put("WAREHOUSE_DB_POOL_SIZE", "500");
        env.put("PORTAL_TIME_ZONE", "Mars/Olympus");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AppConfig.from(env));
        assertTrue(e.getMessage().contains("WAREHOUSE_DB_POOL_SIZE"));
        assertTrue(e.getMessage().contains("PORTAL_TIME_ZONE"));
    }

    @Test
    void passwordsAreNeverPrinted() {
        String text = AppConfig.from(complete()).toString();
        assertFalse(text.contains("secret-portal"));
        assertFalse(text.contains("secret-report"));
    }
}
