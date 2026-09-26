package com.reportingportal.report;

import com.reportingportal.auth.AuthenticatedUser;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.reportingportal.report.ColumnType.DATE;
import static com.reportingportal.report.ColumnType.INTEGER;
import static com.reportingportal.report.ColumnType.MONEY;
import static com.reportingportal.report.ColumnType.MONTH;
import static com.reportingportal.report.ColumnType.PERCENT;
import static com.reportingportal.report.ColumnType.TEXT;
import static com.reportingportal.report.ColumnType.YEAR;
import static com.reportingportal.report.SortDirection.ASC;

/**
 * The 12 reports, one per warehouse view.
 *
 * The definitions (view, columns, parameters, SQL fragments, sort whitelist)
 * are written here in code. Which roles may see each report is read from the
 * report_roles table when the app starts. At startup the report codes here and
 * the rows in the reports table must match exactly, or the app refuses to start,
 * so a report can never exist without an access rule.
 */
public final class ReportRegistry {

    /** Keeps the order of {@link #definitions()}, which is the order of the menu. */
    private final Map<String, ReportDefinition> reportsByCode;

    /** Builds the registry from definitions and their roles; used by {@link #load} and by tests. */
    public ReportRegistry(List<ReportDefinition> definitions, Map<String, Set<String>> rolesByCode) {
        checkSameCodes(definitions, rolesByCode.keySet());
        Map<String, ReportDefinition> byCode = new LinkedHashMap<>();
        for (ReportDefinition definition : definitions) {
            Set<String> roles = rolesByCode.get(definition.getCode());
            if (roles.isEmpty()) {
                throw new IllegalStateException("Report '" + definition.getCode()
                        + "' has no rows in report_roles, so nobody could see it");
            }
            byCode.put(definition.getCode(), definition.withRoles(roles));
        }
        this.reportsByCode = byCode;
    }

    /** Reads the reports and report_roles tables and builds the registry. Called once at startup. */
    public static ReportRegistry load(DataSource dataSource) throws SQLException {
        return new ReportRegistry(definitions(), readRolesByReport(dataSource));
    }

    public Optional<ReportDefinition> find(String code) {
        return Optional.ofNullable(reportsByCode.get(code));
    }

    public Collection<ReportDefinition> all() {
        return reportsByCode.values();
    }

    /** The reports this user may see, in menu order. */
    public List<ReportDefinition> visibleTo(AuthenticatedUser user) {
        List<ReportDefinition> visible = new ArrayList<>();
        for (ReportDefinition report : reportsByCode.values()) {
            if (AccessPolicy.canView(user, report)) {
                visible.add(report);
            }
        }
        return visible;
    }

    /** Every report code in reports, with the roles from report_roles (possibly none). */
    private static Map<String, Set<String>> readRolesByReport(DataSource dataSource) throws SQLException {
        String sql = "SELECT r.report_code, rr.role_code "
                   + "FROM reports r LEFT JOIN report_roles rr ON rr.report_code = r.report_code";
        Map<String, Set<String>> rolesByCode = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Set<String> roles = rolesByCode.computeIfAbsent(rs.getString("report_code"), code -> new HashSet<>());
                String role = rs.getString("role_code");
                if (role != null) {
                    roles.add(role);
                }
            }
        }
        return rolesByCode;
    }

    private static void checkSameCodes(List<ReportDefinition> definitions, Set<String> databaseCodes) {
        Set<String> javaCodes = new TreeSet<>();
        for (ReportDefinition definition : definitions) {
            if (!javaCodes.add(definition.getCode())) {
                throw new IllegalStateException("Report code defined twice: " + definition.getCode());
            }
        }
        Set<String> missingInDatabase = new TreeSet<>(javaCodes);
        missingInDatabase.removeAll(databaseCodes);
        Set<String> missingInJava = new TreeSet<>(databaseCodes);
        missingInJava.removeAll(javaCodes);
        if (!missingInDatabase.isEmpty() || !missingInJava.isEmpty()) {
            throw new IllegalStateException("Report registry and the reports table differ."
                    + " Defined in Java but not in the reports table: " + missingInDatabase
                    + ". In the reports table but not defined in Java: " + missingInJava + ".");
        }
    }

    // =====================================================================
    // The definitions. Each view's business question is the warehouse's own
    // COMMENT ON TABLE text. Column names are exactly the view's columns.
    // =====================================================================

    public static List<ReportDefinition> definitions() {
        return List.of(
                monthlyRevenue(),
                revenueGroupingSets(),
                weekdayOrders(),
                lateDelivery(),
                paymentMix(),
                revenueStateCategory(),
                topCategoriesByState(),
                sellerRank(),
                categoryQuarters(),
                cohortRetention(),
                repeatPurchaseGap(),
                customerMoves());
    }

    static ReportDefinition monthlyRevenue() {
        return ReportDefinition.builder("monthly_revenue")
                .title("Monthly revenue trend")
                .question("How is revenue trending month by month, how does each month compare with the previous one, and what is the running total for the year?")
                .view("v_rpt_monthly_revenue")
                .column("month_start", "Month", MONTH)
                .column("order_count", "Orders", INTEGER)
                .column("revenue", "Revenue (R$)", MONEY)
                .column("avg_order_value", "Avg order value (R$)", MONEY)
                .column("prev_month_revenue", "Previous month (R$)", MONEY)
                .column("mom_growth_pct", "Change vs previous month (%)", PERCENT)
                .column("ytd_revenue", "Year to date (R$)", MONEY)
                .param(ParamDef.month("from_month", "From month", "month_start >= ?"))
                .param(ParamDef.month("to_month", "To month", "month_start <= ?"))
                .rangeRule("from_month", "to_month")
                .sortable("month_start", "revenue", "order_count", "avg_order_value", "mom_growth_pct")
                .defaultSort("month_start", ASC)
                .tieBreaker("month_start")
                .chart(ChartSpec.of(ChartSpec.Type.LINE, "month_start", "revenue"))
                .build();
    }

    static ReportDefinition revenueGroupingSets() {
        return ReportDefinition.builder("revenue_grouping_sets")
                .title("Revenue by year and payment type")
                .question("What is revenue by year, by payment type, and in total, all in one result set?")
                .view("v_rpt_revenue_grouping_sets")
                .column("breakdown", "Breakdown", TEXT)
                .column("year_num", "Year", YEAR)
                .column("payment_type", "Payment type", TEXT)
                .column("order_count", "Orders", INTEGER)
                .column("revenue", "Revenue (R$)", MONEY)
                .param(ParamDef.choice("breakdown", "Breakdown", List.of("YEAR", "PAYMENT TYPE", "TOTAL"), "breakdown = ?"))
                .sortable("breakdown", "year_num", "payment_type", "revenue", "order_count")
                .defaultSort("breakdown", ASC)
                .tieBreaker("year_num", "payment_type")
                .build();
    }

    static ReportDefinition weekdayOrders() {
        return ReportDefinition.builder("weekday_orders")
                .title("Orders by weekday, 2017 vs 2018")
                .question("Which weekday gets the most orders, and did that change between 2017 and 2018?")
                .view("v_rpt_weekday_orders_pivot")
                .column("day_of_week_num", "Day no.", INTEGER)
                .column("day_name", "Weekday", TEXT)
                .column("orders_2017", "Orders 2017", INTEGER)
                .column("orders_2018", "Orders 2018", INTEGER)
                // No filter fits a 7-row pivot; the form offers sorting only.
                .sortable("day_of_week_num", "orders_2017", "orders_2018")
                .defaultSort("day_of_week_num", ASC)
                .tieBreaker("day_of_week_num")
                .build();
    }

    static ReportDefinition lateDelivery() {
        return ReportDefinition.builder("late_delivery")
                .title("Late deliveries by state and year")
                .question("What share of delivered orders arrived after the estimated delivery date, by customer state and year, with every subtotal?")
                .view("v_rpt_late_delivery_cube")
                .column("customer_state", "Customer state", TEXT)
                .column("order_year", "Order year", TEXT)
                .column("grouping_level", "Level (0 = detail)", INTEGER)
                .column("delivered_orders", "Delivered orders", INTEGER)
                .column("late_orders", "Late orders", INTEGER)
                .column("late_pct", "Late (%)", PERCENT)
                .param(ParamDef.state("state", "Customer state", "customer_state = ?"))
                // order_year is VARCHAR2 in the view ('2017' or 'ALL YEARS'), so it is bound as text.
                .param(ParamDef.choice("order_year", "Order year", List.of("2016", "2017", "2018"), "order_year = ?"))
                .param(ParamDef.flag("hide_subtotals", "Hide subtotal rows", "grouping_level = 0"))
                .sortable("customer_state", "order_year", "late_pct", "delivered_orders")
                .defaultSort("customer_state", ASC)
                .tieBreaker("customer_state", "order_year")
                .build();
    }

    static ReportDefinition paymentMix() {
        return ReportDefinition.builder("payment_mix")
                .title("Payment mix by month")
                .question("How does the share of revenue paid by credit card, boleto, voucher and debit card shift month by month?")
                .view("v_rpt_payment_mix_monthly")
                .column("month_start", "Month", MONTH)
                .column("credit_card_revenue", "Credit card (R$)", MONEY)
                .column("boleto_revenue", "Boleto (R$)", MONEY)
                .column("voucher_revenue", "Voucher (R$)", MONEY)
                .column("debit_card_revenue", "Debit card (R$)", MONEY)
                .column("other_revenue", "Other (R$)", MONEY)
                .column("total_revenue", "Total (R$)", MONEY)
                .column("credit_card_share_pct", "Credit card share (%)", PERCENT)
                .column("boleto_share_pct", "Boleto share (%)", PERCENT)
                .param(ParamDef.month("from_month", "From month", "month_start >= ?"))
                .param(ParamDef.month("to_month", "To month", "month_start <= ?"))
                .rangeRule("from_month", "to_month")
                .sortable("month_start", "total_revenue", "credit_card_share_pct", "boleto_share_pct")
                .defaultSort("month_start", ASC)
                .tieBreaker("month_start")
                .chart(ChartSpec.of(ChartSpec.Type.STACKED_BAR, "month_start",
                        "credit_card_revenue", "boleto_revenue", "voucher_revenue", "debit_card_revenue", "other_revenue"))
                .build();
    }

    static ReportDefinition revenueStateCategory() {
        return ReportDefinition.builder("revenue_state_category")
                .title("Revenue by state and category")
                .question("Which customer states and product categories drive revenue, with a subtotal per state and a grand total?")
                .view("v_rpt_revenue_state_category")
                .column("customer_state", "Customer state", TEXT)
                .column("category", "Category", TEXT)
                .column("grouping_level", "Level (0 = detail)", INTEGER)
                .column("order_count", "Orders", INTEGER)
                .column("revenue", "Revenue (R$)", MONEY)
                .param(ParamDef.state("state", "Customer state", "customer_state = ?"))
                .param(ParamDef.category("category", "Category", "category = ?"))
                .param(ParamDef.flag("hide_subtotals", "Hide subtotal rows", "grouping_level = 0"))
                .sortable("customer_state", "category", "revenue", "order_count")
                .defaultSort("customer_state", ASC)
                // Within a state: detail rows (level 0) before the state subtotal (level 1).
                .tieBreaker("grouping_level", "category")
                .build();
    }

    static ReportDefinition topCategoriesByState() {
        return ReportDefinition.builder("top_categories_by_state")
                .title("Top categories in each state")
                .question("What are the top 3 product categories by revenue in each customer state?")
                .view("v_rpt_top_categories_by_state")
                .column("customer_state", "Customer state", TEXT)
                .column("category_rank", "Rank", INTEGER)
                .column("category", "Category", TEXT)
                .column("revenue", "Revenue (R$)", MONEY)
                .column("order_count", "Orders", INTEGER)
                .param(ParamDef.state("state", "Customer state", "customer_state = ?"))
                .param(ParamDef.integer("top_n", "Top N (1 to 3)", 1, 3, "category_rank <= ?"))
                .sortable("customer_state", "category_rank", "revenue")
                .defaultSort("customer_state", ASC)
                .tieBreaker("category_rank", "category")
                .build();
    }

    static ReportDefinition sellerRank() {
        return ReportDefinition.builder("seller_rank")
                .title("Leading sellers in each state")
                .question("Who are the 10 leading sellers in each seller state, and what share of that state's revenue does each one hold?")
                .view("v_rpt_seller_rank_in_state")
                .column("seller_state", "Seller state", TEXT)
                .column("seller_rank", "Rank", INTEGER)
                .column("seller_id", "Seller ID", TEXT)
                .column("seller_city", "Seller city", TEXT)
                .column("revenue", "Revenue (R$)", MONEY)
                .column("order_count", "Orders", INTEGER)
                .column("state_revenue_share_pct", "Share of state revenue (%)", PERCENT)
                .param(ParamDef.state("seller_state", "Seller state", "seller_state = ?"))
                .param(ParamDef.integer("top_n", "Top N (1 to 10)", 1, 10, "seller_rank <= ?"))
                .sortable("seller_state", "seller_rank", "revenue", "state_revenue_share_pct")
                .defaultSort("seller_state", ASC)
                .tieBreaker("seller_rank", "seller_id")
                .build();
    }

    static ReportDefinition categoryQuarters() {
        Map<String, String> quarterColumns = new LinkedHashMap<>();
        quarterColumns.put("2017_Q1", "rev_2017_q1");
        quarterColumns.put("2017_Q2", "rev_2017_q2");
        quarterColumns.put("2017_Q3", "rev_2017_q3");
        quarterColumns.put("2017_Q4", "rev_2017_q4");
        quarterColumns.put("2018_Q1", "rev_2018_q1");
        quarterColumns.put("2018_Q2", "rev_2018_q2");
        quarterColumns.put("2018_Q3", "rev_2018_q3");
        quarterColumns.put("2018_Q4", "rev_2018_q4");

        return ReportDefinition.builder("category_quarters")
                .title("Top categories by quarter")
                .question("How does each product category's revenue compare across the quarters of 2017 and 2018?")
                .view("v_rpt_category_quarter_pivot")
                .column("category", "Category", TEXT)
                .column("rev_2017_q1", "2017 Q1 (R$)", MONEY)
                .column("rev_2017_q2", "2017 Q2 (R$)", MONEY)
                .column("rev_2017_q3", "2017 Q3 (R$)", MONEY)
                .column("rev_2017_q4", "2017 Q4 (R$)", MONEY)
                .column("rev_2018_q1", "2018 Q1 (R$)", MONEY)
                .column("rev_2018_q2", "2018 Q2 (R$)", MONEY)
                .column("rev_2018_q3", "2018 Q3 (R$)", MONEY)
                .column("rev_2018_q4", "2018 Q4 (R$)", MONEY)
                .param(ParamDef.category("category", "Category", "category = ?"))
                // Dynamic column selection: the user picks a quarter, the SQL gets the
                // matching constant column name from this whitelist.
                .param(ParamDef.column("quarter", "Rank by quarter", quarterColumns, "2018_Q2"))
                .param(ParamDef.rowLimit("top_n", "Top N (1 to 50)", 1, 50, 10))
                .orderByColumnParam("quarter")
                .tieBreaker("category")
                // At most 50 rows (top N), so one page always holds the whole result.
                .notPaged()
                .chart(ChartSpec.seriesFromParam(ChartSpec.Type.BAR, "category", "quarter"))
                .build();
    }

    static ReportDefinition cohortRetention() {
        return ReportDefinition.builder("cohort_retention")
                .title("Customer cohort retention")
                .question("Of the customers who first bought in month X, what percentage bought again 1, 2, 3 ... months later?")
                .view("v_rpt_cohort_retention")
                .column("cohort_month", "Cohort (first purchase)", MONTH)
                .column("months_since_first", "Months since first purchase", INTEGER)
                .column("cohort_size", "Cohort size", INTEGER)
                .column("active_customers", "Active customers", INTEGER)
                .column("retention_pct", "Retention (%)", PERCENT)
                .param(ParamDef.month("from_cohort", "From cohort month", "cohort_month >= ?"))
                .param(ParamDef.month("to_cohort", "To cohort month", "cohort_month <= ?"))
                .param(ParamDef.integer("max_months", "Up to months since first purchase (0 to 25)", 0, 25, "months_since_first <= ?"))
                .rangeRule("from_cohort", "to_cohort")
                .sortable("cohort_month", "months_since_first", "retention_pct", "cohort_size")
                .defaultSort("cohort_month", ASC)
                .tieBreaker("cohort_month", "months_since_first")
                .build();
    }

    static ReportDefinition repeatPurchaseGap() {
        return ReportDefinition.builder("repeat_purchase_gap")
                .title("Repeat purchase gap")
                .question("For customers who ordered more than once, how many days pass between one order and the next?")
                .view("v_rpt_repeat_purchase_gap")
                .column("customer_unique_id", "Customer", TEXT)
                .column("order_number", "Order no.", INTEGER)
                .column("total_orders", "Orders in total", INTEGER)
                .column("order_id", "Order ID", TEXT)
                .column("order_date", "Order date", DATE)
                .column("previous_order_date", "Previous order", DATE)
                .column("days_since_previous", "Days since previous", INTEGER)
                .column("next_order_date", "Next order", DATE)
                .param(ParamDef.date("from_date", "Order date from", "order_date >= ?"))
                // Inclusive end date: the view's dates come from dim_date.calendar_date, all at midnight.
                .param(ParamDef.date("to_date", "Order date to", "order_date <= ?"))
                .param(ParamDef.integer("min_gap_days", "At least this many days since previous order (0 to 1000)", 0, 1000,
                        "days_since_previous >= ?"))
                .rangeRule("from_date", "to_date")
                .sortable("order_date", "days_since_previous", "total_orders", "customer_unique_id")
                .defaultSort("order_date", ASC)
                .tieBreaker("order_id")
                .build();
    }

    static ReportDefinition customerMoves() {
        return ReportDefinition.builder("customer_moves")
                .title("Customer address changes (SCD2)")
                .question("Which customers changed address, from where to where, and when did the warehouse record the change?")
                .view("v_rpt_customer_moves")
                .column("customer_unique_id", "Customer", TEXT)
                .column("version", "Version", INTEGER)
                .column("from_city", "From city", TEXT)
                .column("from_state", "From state", TEXT)
                .column("from_zip_prefix", "From ZIP prefix", TEXT)
                .column("to_city", "To city", TEXT)
                .column("to_state", "To state", TEXT)
                .column("to_zip_prefix", "To ZIP prefix", TEXT)
                .column("changed_state", "Changed state?", TEXT)
                .column("recorded_at", "Recorded", DATE)
                .column("is_current", "Current?", TEXT)
                .param(ParamDef.state("to_state", "Moved to state", "to_state = ?"))
                .param(ParamDef.flag("changed_state_only", "Only moves to another state", "changed_state = 'Y'"))
                .sortable("to_state", "from_state", "recorded_at", "customer_unique_id")
                .defaultSort("to_state", ASC)
                .tieBreaker("customer_unique_id", "version")
                .build();
    }
}
