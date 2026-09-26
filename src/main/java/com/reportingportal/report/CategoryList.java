package com.reportingportal.report;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The product categories that exist in the warehouse, read once at startup.
 *
 * They are the allowed values of every CATEGORY parameter: the form shows them
 * as a drop-down, and ParamValidator rejects anything else. Categories only
 * change when the warehouse is reloaded with new data, so a restart picks them up.
 */
public final class CategoryList {

    /** Detail rows (grouping_level 0) have a real category; subtotal rows say 'ALL CATEGORIES'. */
    private static final String SQL =
            "SELECT DISTINCT category FROM v_rpt_revenue_state_category "
          + "WHERE grouping_level = 0 ORDER BY category";

    private CategoryList() {
    }

    public static List<String> load(DataSource warehouse) throws SQLException {
        List<String> categories = new ArrayList<>();
        try (Connection connection = warehouse.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString(1));
            }
        }
        return List.copyOf(categories);
    }
}
