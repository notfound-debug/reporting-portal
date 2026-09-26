package com.reportingportal.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Creates the web app's two connection pools.
 *
 * Opening an Oracle connection means a network round trip, authentication and a
 * new server session, which takes tens of milliseconds. A pool opens a few
 * connections once and lends them out, so each request only borrows one.
 *
 * Why two pools, with two different database users:
 *   portal     PORTAL user: the portal's own tables (logins, roles, schedules).
 *              Small, indexed lookups, so 2 connections are plenty.
 *   warehouse  the warehouse's read-only reporting user, which can only SELECT
 *              the 12 report views. Every report query runs here, so even a
 *              mistake in report SQL could never read the users table or the
 *              password hashes, or change anything.
 */
public final class HikariFactory {

    private HikariFactory() {
    }

    public static HikariDataSource portalPool(AppConfig config) {
        return create("portal", config.dbUrl(), config.portalUser(), config.portalPassword(),
                config.portalPoolSize(), null);
    }

    public static HikariDataSource warehousePool(AppConfig config) {
        // Every new connection starts with CURRENT_SCHEMA = DW, so report SQL can say
        // v_rpt_monthly_revenue instead of dw.v_rpt_monthly_revenue, and the schema name
        // lives only in configuration. This changes how names are looked up, not what
        // the user is allowed to do. The name was checked against a strict pattern in AppConfig.
        String initSql = "ALTER SESSION SET CURRENT_SCHEMA = " + config.warehouseSchema();
        return create("warehouse", config.dbUrl(), config.warehouseUser(), config.warehousePassword(),
                config.warehousePoolSize(), initSql);
    }

    private static HikariDataSource create(String poolName, String url, String user, String password,
                                           int size, String connectionInitSql) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName(poolName);
        hikari.setJdbcUrl(url);
        hikari.setUsername(user);
        hikari.setPassword(password);

        // Name the driver class explicitly. Tomcat initialises java.sql.DriverManager
        // before the web app starts, so a driver inside WEB-INF/lib is not always
        // found automatically by the JDBC URL.
        hikari.setDriverClassName("oracle.jdbc.OracleDriver");

        // A fixed-size pool (minimum idle = maximum), as HikariCP recommends.
        // Oracle XE runs on at most 2 CPU threads; about cores * 2 + 1 = 5 connections
        // doing report queries keeps it busy without queueing work inside the database.
        hikari.setMaximumPoolSize(size);
        hikari.setMinimumIdle(size);

        // Wait at most 5 s for a free connection, then fail with an error page
        // instead of leaving the user's request hanging.
        hikari.setConnectionTimeout(5_000);

        // Replace each connection after 30 minutes, before any firewall or database
        // idle timeout can cut it underneath us.
        hikari.setMaxLifetime(1_800_000);

        if (connectionInitSql != null) {
            hikari.setConnectionInitSql(connectionInitSql);
        }

        // HikariDataSource opens its first connection here; if the database is
        // unreachable or the password is wrong, startup fails right away.
        return new HikariDataSource(hikari);
    }
}
