package com.reportingportal.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Creates the one connection pool the whole web app shares.
 *
 * Opening an Oracle connection means a network round trip, authentication and a
 * new server session, which takes tens of milliseconds. A pool opens a few
 * connections once and lends them out, so each request only borrows one.
 */
public final class HikariFactory {

    private HikariFactory() {
    }

    public static HikariDataSource create(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("portal");
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUser());
        hikari.setPassword(config.dbPassword());

        // Name the driver class explicitly. Tomcat initialises java.sql.DriverManager
        // before the web app starts, so a driver inside WEB-INF/lib is not always
        // found automatically by the JDBC URL.
        hikari.setDriverClassName("oracle.jdbc.OracleDriver");

        // A fixed-size pool (minimum idle = maximum), as HikariCP recommends.
        // Oracle XE runs on at most 2 CPU threads; about cores * 2 + 1 = 5 connections
        // keeps it busy without queueing work inside the database.
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setMinimumIdle(config.poolSize());

        // Wait at most 5 s for a free connection, then fail with an error page
        // instead of leaving the user's request hanging.
        hikari.setConnectionTimeout(5_000);

        // Replace each connection after 30 minutes, before any firewall or database
        // idle timeout can cut it underneath us.
        hikari.setMaxLifetime(1_800_000);

        // HikariDataSource opens its first connection here; if the database is
        // unreachable or the password is wrong, startup fails right away.
        return new HikariDataSource(hikari);
    }
}
