-- =============================================================================
-- 02_seed.sql   (run by bin/db-setup.sh as the PORTAL user)
-- Roles, the three demo users (one per role), the 12 reports, and which roles
-- may see each report.
--
-- Demo passwords (also in the README): viewer / Viewer#2026,
-- analyst / Analyst#2026, admin / Admin#2026. Only their BCrypt hashes are
-- stored, generated with bin/hash-password.sh. Change them on any shared machine:
--   bin/hash-password.sh, then UPDATE users SET password_hash = '...' WHERE username = '...';
-- =============================================================================

INSERT INTO roles (role_code, description) VALUES ('VIEWER',  'Company-level summary reports');
INSERT INTO roles (role_code, description) VALUES ('ANALYST', 'Summary reports plus state, category, seller and customer-behaviour detail');
INSERT INTO roles (role_code, description) VALUES ('ADMIN',   'Every report, including customer address history, and every user''s exports');

INSERT INTO users (username, password_hash, display_name)
VALUES ('viewer',  '$2a$12$puj/B5GD2kd8ohmT449aqODD13YRjH/gu/j3OZvs.k/ThlNSwkGUO', 'Demo Viewer');
INSERT INTO users (username, password_hash, display_name)
VALUES ('analyst', '$2a$12$dJhtoYJ3MUjkops5IBn13Oe9YpF0wNpps1BaJ6J7DgpdRasvJEILy', 'Demo Analyst');
INSERT INTO users (username, password_hash, display_name)
VALUES ('admin',   '$2a$12$.wDLEnym2354SnUxpIb/XuUgr2BTd2UysM9KAjKhPWOerNcaIczue', 'Demo Admin');

-- user_id values are generated, so look them up by username.
INSERT INTO user_roles (user_id, role_code) SELECT user_id, 'VIEWER'  FROM users WHERE username = 'viewer';
INSERT INTO user_roles (user_id, role_code) SELECT user_id, 'ANALYST' FROM users WHERE username = 'analyst';
INSERT INTO user_roles (user_id, role_code) SELECT user_id, 'ADMIN'   FROM users WHERE username = 'admin';

-- Must match the report codes in ReportRegistry exactly; the app checks at startup.
INSERT INTO reports (report_code) VALUES ('monthly_revenue');
INSERT INTO reports (report_code) VALUES ('revenue_grouping_sets');
INSERT INTO reports (report_code) VALUES ('weekday_orders');
INSERT INTO reports (report_code) VALUES ('late_delivery');
INSERT INTO reports (report_code) VALUES ('payment_mix');
INSERT INTO reports (report_code) VALUES ('revenue_state_category');
INSERT INTO reports (report_code) VALUES ('top_categories_by_state');
INSERT INTO reports (report_code) VALUES ('seller_rank');
INSERT INTO reports (report_code) VALUES ('category_quarters');
INSERT INTO reports (report_code) VALUES ('cohort_retention');
INSERT INTO reports (report_code) VALUES ('repeat_purchase_gap');
INSERT INTO reports (report_code) VALUES ('customer_moves');

-- Access rules, one row per (report, role). Assigned explicitly, with no role
-- hierarchy, so "what can ANALYST see?" is a single SELECT on this table.
--   VIEWER : company-level summaries (4 reports)
--   ANALYST: + state, category, seller and customer-behaviour detail (11)
--   ADMIN  : + customer address history (all 12)
INSERT INTO report_roles (report_code, role_code) VALUES ('monthly_revenue',         'VIEWER');
INSERT INTO report_roles (report_code, role_code) VALUES ('monthly_revenue',         'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('monthly_revenue',         'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('revenue_grouping_sets',   'VIEWER');
INSERT INTO report_roles (report_code, role_code) VALUES ('revenue_grouping_sets',   'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('revenue_grouping_sets',   'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('weekday_orders',          'VIEWER');
INSERT INTO report_roles (report_code, role_code) VALUES ('weekday_orders',          'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('weekday_orders',          'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('late_delivery',           'VIEWER');
INSERT INTO report_roles (report_code, role_code) VALUES ('late_delivery',           'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('late_delivery',           'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('payment_mix',             'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('payment_mix',             'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('revenue_state_category',  'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('revenue_state_category',  'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('top_categories_by_state', 'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('top_categories_by_state', 'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('seller_rank',             'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('seller_rank',             'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('category_quarters',       'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('category_quarters',       'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('cohort_retention',        'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('cohort_retention',        'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('repeat_purchase_gap',     'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('repeat_purchase_gap',     'ADMIN');
INSERT INTO report_roles (report_code, role_code) VALUES ('customer_moves',          'ADMIN');

COMMIT;
