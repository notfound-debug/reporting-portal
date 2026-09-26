-- =============================================================================
-- 00_grants.sql   (run by bin/db-setup.sh as SYSDBA inside XEPDB1)
-- Lets PORTAL read the 12 warehouse report views, and nothing else in the
-- warehouse schema: no fact or dimension table, no staging, no packages.
--
-- Safe to run again at any time (bin/db-setup.sh --grants). It must be re-run
-- after the warehouse's install.sh --reset, because dropping a view also drops
-- every grant on it.
--
-- The synonyms let the Java code say v_rpt_monthly_revenue instead of
-- dw.v_rpt_monthly_revenue, so the warehouse schema name lives only here.
-- A view keeps working through a SELECT on it without any grant on the tables
-- underneath: the view runs with its owner's (DW's) rights.
-- =============================================================================

GRANT SELECT ON &dw_schema..v_rpt_monthly_revenue         TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_revenue_state_category  TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_payment_mix_monthly     TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_top_categories_by_state TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_seller_rank_in_state    TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_cohort_retention        TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_late_delivery_cube      TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_category_quarter_pivot  TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_repeat_purchase_gap     TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_revenue_grouping_sets   TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_weekday_orders_pivot    TO &portal_user;
GRANT SELECT ON &dw_schema..v_rpt_customer_moves          TO &portal_user;

-- (sqlplus: "&dw_schema.." means the variable followed by one literal dot.)
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_monthly_revenue         FOR &dw_schema..v_rpt_monthly_revenue;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_revenue_state_category  FOR &dw_schema..v_rpt_revenue_state_category;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_payment_mix_monthly     FOR &dw_schema..v_rpt_payment_mix_monthly;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_top_categories_by_state FOR &dw_schema..v_rpt_top_categories_by_state;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_seller_rank_in_state    FOR &dw_schema..v_rpt_seller_rank_in_state;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_cohort_retention        FOR &dw_schema..v_rpt_cohort_retention;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_late_delivery_cube      FOR &dw_schema..v_rpt_late_delivery_cube;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_category_quarter_pivot  FOR &dw_schema..v_rpt_category_quarter_pivot;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_repeat_purchase_gap     FOR &dw_schema..v_rpt_repeat_purchase_gap;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_revenue_grouping_sets   FOR &dw_schema..v_rpt_revenue_grouping_sets;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_weekday_orders_pivot    FOR &dw_schema..v_rpt_weekday_orders_pivot;
CREATE OR REPLACE SYNONYM &portal_user..v_rpt_customer_moves          FOR &dw_schema..v_rpt_customer_moves;
