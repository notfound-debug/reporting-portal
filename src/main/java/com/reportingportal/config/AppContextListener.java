package com.reportingportal.config;

import com.reportingportal.report.CategoryList;
import com.reportingportal.report.ReportRegistry;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts and stops the application's shared objects together with the web app.
 *
 * Tomcat calls contextInitialized once, before the first request is served, and
 * contextDestroyed once when the app is stopped or redeployed. Objects created
 * here are stored as application-scope attributes, so every servlet can reach them.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    /** Application-scope attribute names. */
    public static final String CONFIG = "portal.config";
    /** Pool for the portal's own tables (PORTAL user). */
    public static final String DATA_SOURCE = "portal.dataSource";
    /** Pool for the report views (the warehouse's read-only reporting user). */
    public static final String WAREHOUSE_DATA_SOURCE = "portal.warehouseDataSource";
    public static final String REGISTRY = "portal.registry";
    public static final String CATEGORIES = "portal.categories";

    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    private HikariDataSource portalPool;
    private HikariDataSource warehousePool;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            AppConfig config = AppConfig.fromEnvironment();
            LOG.info("Starting Reporting Portal with " + config);

            portalPool = HikariFactory.portalPool(config);
            warehousePool = HikariFactory.warehousePool(config);
            LOG.info("Connection pools started");

            // Fails startup if the Java report codes and the reports table differ.
            ReportRegistry registry = ReportRegistry.load(portalPool);
            LOG.info("Report registry loaded: " + registry.all().size() + " reports");

            // The allowed values of every "category" parameter, read once from the warehouse.
            List<String> categories = CategoryList.load(warehousePool);
            LOG.info("Product categories loaded: " + categories.size());

            ServletContext context = event.getServletContext();
            context.setAttribute(CONFIG, config);
            context.setAttribute(DATA_SOURCE, portalPool);
            context.setAttribute(WAREHOUSE_DATA_SOURCE, warehousePool);
            context.setAttribute(REGISTRY, registry);
            context.setAttribute(CATEGORIES, categories);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Reporting Portal failed to start: " + e.getMessage(), e);
            closePools();
            throw new IllegalStateException("Could not read the portal or warehouse tables", e);
        } catch (RuntimeException e) {
            // Throwing here makes Tomcat mark the web app as failed to start, which
            // is what we want: better no app than one without a database.
            LOG.log(Level.SEVERE, "Reporting Portal failed to start: " + e.getMessage(), e);
            closePools();
            throw e;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        closePools();
        deregisterJdbcDrivers();
        LOG.info("Reporting Portal stopped");
    }

    private void closePools() {
        if (warehousePool != null) {
            warehousePool.close();
            warehousePool = null;
        }
        if (portalPool != null) {
            portalPool.close();
            portalPool = null;
        }
        LOG.info("Connection pools closed");
    }

    /**
     * The Oracle driver jar is inside this WAR (WEB-INF/lib) and registers itself
     * with DriverManager, which belongs to the JVM, not to the web app. If it is
     * not removed on shutdown, DriverManager keeps a reference to this web app's
     * class loader and none of its classes can be garbage-collected after a
     * redeploy (Tomcat warns about exactly this leak).
     */
    private void deregisterJdbcDrivers() {
        ClassLoader webAppLoader = getClass().getClassLoader();
        for (Driver driver : Collections.list(DriverManager.getDrivers())) {
            if (driver.getClass().getClassLoader() == webAppLoader) {
                try {
                    DriverManager.deregisterDriver(driver);
                    LOG.info("Deregistered JDBC driver " + driver.getClass().getName());
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "Could not deregister JDBC driver " + driver, e);
                }
            }
        }
    }
}
