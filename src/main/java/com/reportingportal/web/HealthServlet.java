package com.reportingportal.web;

import com.reportingportal.config.AppContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * GET /health: is the database reachable, and can the portal read the warehouse?
 *
 * Public (no login), so a monitor or the smoke test can call it. It shows only
 * UP/DOWN and a timing, never versions, URLs or error text, because anyone can see it.
 */
@WebServlet("/health")
public class HealthServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(HealthServlet.class.getName());

    /** Proves the pool can hand out a working connection. */
    private static final String DATABASE_CHECK = "SELECT 1 FROM DUAL";

    /**
     * Proves the PORTAL user can still read a warehouse view. The warehouse's
     * reset drops its views, and with them the grants; this check catches that.
     */
    private static final String WAREHOUSE_CHECK = "SELECT COUNT(*) FROM v_rpt_weekday_orders_pivot";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        DataSource dataSource = (DataSource) getServletContext().getAttribute(AppContextListener.DATA_SOURCE);

        long start = System.nanoTime();
        boolean databaseUp = runsOk(dataSource, DATABASE_CHECK);
        boolean warehouseReadable = databaseUp && runsOk(dataSource, WAREHOUSE_CHECK);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        boolean healthy = databaseUp && warehouseReadable;
        request.setAttribute("healthy", healthy);
        request.setAttribute("databaseUp", databaseUp);
        request.setAttribute("warehouseReadable", warehouseReadable);
        request.setAttribute("elapsedMillis", elapsedMillis);

        // 503 lets a monitor tell "down" from "up" without reading the page.
        response.setStatus(healthy ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/jsp/health.jsp").forward(request, response);
    }

    private boolean runsOk(DataSource dataSource, String sql) {
        // try-with-resources closes the result set, the statement and the connection
        // (which returns it to the pool) even if the query throws.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            // The detail goes to the server log only, never to the public page.
            LOG.warning("Health check failed for [" + sql + "]: " + e.getMessage());
            return false;
        }
    }
}
