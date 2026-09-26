# Reporting Portal: Design

Status: **approved 2026-09-26**. All 8 open questions in section n were resolved with the recommended option (see "Decisions" at the end).

Everything the warehouse facts below rely on was measured during discovery against the running warehouse (`../retail`, Oracle 21c XE in container `retail-oracle-1`, schema `DW`, PDB `XEPDB1`).

| View (schema DW) | Rows | Columns (Oracle types) |
|---|---:|---|
| v_rpt_monthly_revenue | 25 | month_start DATE, year_num NUMBER(4), month_num NUMBER(2), order_count, revenue, avg_order_value, prev_month_revenue, mom_growth_pct, ytd_revenue (NUMBER) |
| v_rpt_revenue_state_category | 1,420 | customer_state VARCHAR2 (incl. 'ALL STATES'), category VARCHAR2 (incl. 'ALL CATEGORIES'), grouping_level, order_count, revenue |
| v_rpt_payment_mix_monthly | 24 | month_start DATE, credit_card_revenue, boleto_revenue, voucher_revenue, debit_card_revenue, other_revenue, total_revenue, credit_card_share_pct, boleto_share_pct |
| v_rpt_top_categories_by_state | 81 | customer_state, category_rank, category, revenue, order_count |
| v_rpt_seller_rank_in_state | 148 | seller_state, seller_rank, seller_id, seller_city, revenue, order_count, state_revenue_share_pct |
| v_rpt_cohort_retention | 301 | cohort_month DATE, months_since_first, cohort_size, active_customers, retention_pct |
| v_rpt_late_delivery_cube | 106 | customer_state (incl. 'ALL STATES'), order_year **VARCHAR2** ('2017' or 'ALL YEARS'), grouping_level, delivered_orders, late_orders, late_pct |
| v_rpt_category_quarter_pivot | 74 | category, rev_2017_q1 … rev_2018_q4 (8 NUMBER columns) |
| v_rpt_repeat_purchase_gap | 6,103 | customer_unique_id, order_number, total_orders, order_id, order_date DATE, previous_order_date DATE, days_since_previous NUMBER, next_order_date DATE |
| v_rpt_revenue_grouping_sets | 9 | breakdown ('YEAR' / 'PAYMENT TYPE' / 'TOTAL'), year_num, payment_type, order_count, revenue |
| v_rpt_weekday_orders_pivot | 7 | day_of_week_num, day_name, orders_2017, orders_2018 |
| v_rpt_customer_moves | 249 | customer_unique_id, version, from_city, from_state, from_zip_prefix, to_city, to_state, to_zip_prefix, changed_state CHAR, recorded_at DATE, is_current CHAR |

Other discovery facts:
- 27 customer states and 23 seller states; 74 categories, the longest 45 characters. Months run 2016-09 to 2018-09, and `dim_date` covers 2016-01-01 to 2019-12-31.
- The heaviest real queries take 0.2 s or less, measured with `SET TIMING ON`. That includes an offset page of `v_rpt_repeat_purchase_gap` sorted by gap. So the portal queries the views live, with no cache.
- The warehouse's `bin/install.sh --reset` (which its test suite runs) **drops and recreates every view, and dropping a view deletes its grants**. The portal's setup must therefore be able to re-apply grants (section d).
- curl 8.19 on this machine sends a `Secure` cookie over plain `http://localhost` and `http://127.0.0.1` (tested with a throwaway local server). Browsers do the same for localhost. So the `Secure` flag can stay on permanently (section g).
- This machine has no JDK, Maven or Tomcat. Docker Desktop 29.6 has 8 GB and 16 CPUs. Build and run options are in section l and question 1.

---

## a. Overview

1. The Reporting Portal is a Java 17 web application (Jakarta Servlets, JSP with JSTL, JDBC) running on Tomcat 10.1. It sits in front of the retail warehouse's 12 report views.
2. A user logs in, sees only the reports their role allows, fills in a report form, and gets a sorted, paged HTML table.
3. Form values are validated on the server, then bound into `PreparedStatement` parameters. Sort columns come from a per-report whitelist.
4. Three reports also have a chart page (Chart.js), fed by a JSON endpoint that applies exactly the same role check and validation.
5. Any report can be scheduled as a daily CSV export. A background thread inside the web app runs due schedules, writes the file, and records each run in the database. Users list and download their past exports.
6. The portal has its own Oracle schema `PORTAL` (users, roles, report access, schedules, runs) in the same database as the warehouse. It has read-only `SELECT` on the 12 views and nothing else in `DW`.
7. Security is plain Servlet API code: an authentication filter, BCrypt passwords, session-fixation protection, `HttpOnly`/`Secure`/`SameSite` cookies, CSRF tokens on every POST, and `<c:out>` escaping in every JSP.
8. Tomcat runs in Docker, on the warehouse's Docker network, so the whole system starts with `docker compose`.

## b. Architecture

```
 Browser  (HTML forms, vanilla JS, Chart.js 4 from cdn.jsdelivr.net)
    │  HTTP, 127.0.0.1:8080 only
    ▼
┌──────────────────── container "portal": Tomcat 10.1 on JDK 17 ─────────────────────────────┐
│                                                                                            │
│  Filters, in web.xml order:  SecurityHeadersFilter ─► AuthFilter ─► CsrfFilter             │
│        │                                                                                   │
│        ▼                                                                                   │
│  Servlets (controllers)                                  JSPs in /WEB-INF/jsp (views)      │
│   LoginServlet  LogoutServlet  HealthServlet     ─forward─►  login  health  reports        │
│   ReportListServlet  ReportServlet  ChartPageServlet          report  chart  exports       │
│   ReportDataServlet (JSON)  ExportServlet  ExportDownloadServlet            error          │
│        │                                                                                   │
│        ▼                                                                                   │
│  Model: ReportRegistry  ParamValidator  ReportQuery  AccessPolicy                          │
│         UserDao  ReportDao  ScheduleDao  ExportRunDao  CsvWriter  JsonWriter               │
│        │                            ▲                                                      │
│        │          ExportScheduler ──┘  (1 background thread, wakes every 30 s)             │
│        │                │ writes CSV: .tmp file, then rename                               │
│        ▼                ▼                                                                  │
│  HikariCP pool       /exports ───── bind mount ─────► ./exports on the host (gitignored)   │
│  (5 connections)                                                                           │
│  AppContextListener creates the pool and scheduler at startup and closes both at shutdown  │
└──────┬─────────────────────────────────────────────────────────────────────────────────────┘
       │ JDBC thin: jdbc:oracle:thin:@//oracle:1521/XEPDB1 as user PORTAL
       │ (Docker network retail_default, owned by ../retail)
       ▼
┌──────────────── Oracle 21c XE, container retail-oracle-1, PDB XEPDB1 ─────────────────────┐
│  schema PORTAL  users  roles  user_roles  reports  report_roles                           │
│                 export_schedules  export_runs                                             │
│                 12 private synonyms v_rpt_*  ──────────┐                                  │
│  schema DW      fact_sales, dim_* (not visible to PORTAL)                                 │
│                 12 views v_rpt_*  ◄────────────────────┘  GRANT SELECT to PORTAL, views only │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

## c. MVC layout

**Model:** plain Java classes (registry, validator, query builder, DAOs, writers). No servlet API types appear in them, so they can be unit-tested without Tomcat.
**View:** JSPs under `/WEB-INF/jsp/`. Anything under `WEB-INF` cannot be requested by URL directly, so a JSP only ever renders after a servlet has checked access and prepared its data.
**Controller:** one servlet per URL family, registered with `@WebServlet`.
- **Filters** are declared in `web.xml` rather than with `@WebFilter`. Their order matters, and annotations give no guaranteed order.
- The shared page frame is a JSP tag file, `/WEB-INF/tags/layout.tag` (standard JSP, no extra library).

| URL | Servlet | Methods | JSP | Result |
|---|---|---|---|---|
| `/` | (welcome file `index.jsp`) | GET | `index.jsp` | `<c:redirect url="/reports"/>` |
| `/login` | LoginServlet | GET, POST | `login.jsp` | GET shows the form. A successful POST redirects to `/reports`. A failed POST re-renders the form with a 401 status and one generic message. |
| `/logout` | LogoutServlet | POST only | none | Invalidates the session, then redirects to `/login?loggedOut` |
| `/health` | HealthServlet | GET | `health.jsp` | 200 `UP` or 503 `DOWN` (public; see section g) |
| `/reports` | ReportListServlet | GET | `reports.jsp` | The reports the user's roles allow, with links to the table and chart pages |
| `/reports/{code}` | ReportServlet | GET | `report.jsp` | Form, validation errors, result table, sorting links, paging, and a "Schedule daily CSV" form |
| `/charts/{code}` | ChartPageServlet | GET | `chart.jsp` | Form plus `<canvas>`. Only exists for reports that have a chart (404 otherwise). |
| `/api/reports/{code}` | ReportDataServlet | GET | none (JSON) | Section i |
| `/exports` | ExportServlet | GET, POST | `exports.jsp` | GET lists schedules and runs. POST with `action=create`, `runNow` or `disable` redirects back to `/exports`. |
| `/exports/download` | ExportDownloadServlet | GET `?run={id}` | none (file) | Streams the CSV file |
| errors | `web.xml` `<error-page>` | | `error.jsp` | 403, 404 and 500 pages with a fixed message and never a stack trace |

**Forward vs redirect rules**
1. **GET → forward to a JSP.** The URL stays as the user typed it, so a report URL with its parameters is bookmarkable.
2. **Successful POST → redirect** (Post/Redirect/Get). Refreshing the next page repeats a harmless GET, not the POST: no double login, no duplicate schedule.
3. **POST that fails validation → forward** back to the same JSP with status 400 and the error messages. Nothing has changed, so there is nothing to protect against resubmitting.
4. **Not logged in → redirect to `/login`.** For `/api/*` the response is 401 JSON instead, because a script calling `fetch` cannot follow a redirect to an HTML login page usefully.

**Request flow for one report page, from URL to HTML.** Example: `GET /reports/repeat_purchase_gap?from_date=2017-06-01&min_gap_days=30&sort=days_since_previous&dir=desc&page=2`
1. Tomcat matches `/reports/*` to ReportServlet. First the filter chain runs:
   - SecurityHeadersFilter adds the CSP and related headers.
   - AuthFilter finds `currentUser` in the session. Without it, the request is redirected to `/login`.
   - CsrfFilter lets the request through, because GET changes nothing.
2. `ReportServlet.doGet` takes `repeat_purchase_gap` from `request.getPathInfo()`. `ReportRegistry.find(code)` returns 404 if the code is unknown.
3. `AccessPolicy.canView(user, report)` returns 403 if the user's roles do not intersect the report's roles. This runs **before** any parameter is read, so a forbidden user learns nothing about the report's parameters.
4. `ParamValidator.validate(report, request)` returns either `ValidatedParams` or a map of field → message. On errors: set request attributes, `response.setStatus(400)`, and forward to `report.jsp`, which shows the form with the messages. No SQL runs.
5. `ReportQuery.forPage(report, params)` builds the SQL text from registry constants only, plus an ordered bind list (section f).
6. `ReportDao.run(query)` does the following:
   - borrows a connection from HikariCP;
   - `prepareStatement(sql)`, `setObject` for each bind, `setQueryTimeout(30)`, `setFetchSize(100)`;
   - reads up to 51 rows (50 shown, and the 51st only tells us a next page exists);
   - closes everything in try-with-resources, which returns the connection to the pool.
7. The servlet puts `report`, `params`, `result` (column definitions, rows, `hasNextPage`) and `errors` into **request** scope and forwards to `/WEB-INF/jsp/report.jsp`.
8. `report.jsp` renders the page:
   - It wraps everything in `<t:layout>`.
   - It loops over `result.rows` with `<c:forEach>` and prints each cell with `<c:out>` or `<fmt:formatNumber>`/`<fmt:formatDate>` by column type.
   - It builds the sort and paging links with `<c:url>` + `<c:param>`, which URL-encodes the values.
9. Jasper (Tomcat's JSP engine) compiled `report.jsp` into a servlet on its first request. That generated servlet writes the HTML, and the response is sent.

## d. Portal schema

A separate database user `PORTAL` in the same PDB (`XEPDB1`) as the warehouse.
- **Why the same database:** there is a single XE instance, and a second database would need a database link to read the views.
- **Why a separate schema:** the portal's tables have a different owner, lifecycle and privileges from the warehouse. `PORTAL` can read the 12 views and cannot touch `DW`'s tables at all.

**Setup: `bin/db-setup.sh`**, run from Git Bash. It pipes SQL into `sqlplus` inside the warehouse's Oracle container with `docker exec -i retail-oracle-1 sqlplus -s /nolog`. Passwords go over stdin, never on a command line.
1. As `SYSDBA` (`CONNECT / AS SYSDBA`), which uses OS authentication. The container runs as OS user `oracle` in group `dba`, verified in discovery, so no SYS password is needed.
   - `CREATE USER portal ... QUOTA 50M ON users`
   - `GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO portal`. `CREATE SEQUENCE` is needed because each identity column creates a sequence behind the scenes.
   - 12 explicit `GRANT SELECT ON dw.v_rpt_x TO portal` lines. Explicit lines rather than a loop, so the least-privilege list is readable.
   - 12 `CREATE OR REPLACE SYNONYM portal.v_rpt_x FOR dw.v_rpt_x`, so Java code never names the warehouse schema.
2. As `PORTAL`: `db/01_schema.sql` (tables), then `db/02_seed.sql` (roles, users, reports, report_roles).
3. `bin/db-setup.sh --grants` repeats only the grants and synonyms. Run it after the warehouse's `install.sh --reset`, which drops its views and with them the grants.
4. `bin/db-setup.sh --reset` runs `DROP USER portal CASCADE` first.

**Tables.** Identity columns are used here, where the warehouse uses sequences. The warehouse needed sequences to insert `-1 Unknown` rows. The portal has no such rows, and `GENERATED ALWAYS AS IDENTITY` is the simpler modern choice. All `TIMESTAMP` columns hold **UTC** (section h).

```sql
CREATE TABLE roles (
    role_code    VARCHAR2(20)  PRIMARY KEY,                 -- VIEWER, ANALYST, ADMIN
    description  VARCHAR2(200) NOT NULL
);

CREATE TABLE users (
    user_id        NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username       VARCHAR2(30)  NOT NULL CONSTRAINT users_username_uk UNIQUE
                   CONSTRAINT users_username_ck CHECK (REGEXP_LIKE(username, '^[a-z0-9._-]{3,30}$')),
    password_hash  VARCHAR2(60)  NOT NULL,                  -- BCrypt string, always 60 chars
    display_name   VARCHAR2(100) NOT NULL,
    is_active      CHAR(1) DEFAULT 'Y' NOT NULL CHECK (is_active IN ('Y','N')),
    created_at     TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
    last_login_at  TIMESTAMP
);

CREATE TABLE user_roles (
    user_id    NUMBER       NOT NULL REFERENCES users (user_id),
    role_code  VARCHAR2(20) NOT NULL REFERENCES roles (role_code),
    CONSTRAINT user_roles_pk PRIMARY KEY (user_id, role_code)
);

-- One row per report defined in Java (section e). Its job is to be the foreign-key
-- target, so report_roles and export_schedules can only name real reports.
CREATE TABLE reports (
    report_code  VARCHAR2(40) PRIMARY KEY
                 CHECK (REGEXP_LIKE(report_code, '^[a-z][a-z0-9_]{2,39}$'))
);

CREATE TABLE report_roles (
    report_code  VARCHAR2(40) NOT NULL REFERENCES reports (report_code),
    role_code    VARCHAR2(20) NOT NULL REFERENCES roles (role_code),
    CONSTRAINT report_roles_pk PRIMARY KEY (report_code, role_code)
);

CREATE TABLE export_schedules (
    schedule_id   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       NUMBER        NOT NULL REFERENCES users (user_id),
    report_code   VARCHAR2(40)  NOT NULL REFERENCES reports (report_code),
    params_query  VARCHAR2(1000) NOT NULL,     -- canonical, validated query string, e.g. from_month=2017-01&sort=revenue&dir=desc
    run_time      VARCHAR2(5)   NOT NULL
                  CHECK (REGEXP_LIKE(run_time, '^([01][0-9]|2[0-3]):[0-5][0-9]$')),   -- HH:MM in PORTAL_TIME_ZONE
    next_run_at   TIMESTAMP     NOT NULL,      -- UTC
    is_enabled    CHAR(1) DEFAULT 'Y' NOT NULL CHECK (is_enabled IN ('Y','N')),
    created_at    TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL
);
CREATE INDEX export_schedules_due_ix ON export_schedules (is_enabled, next_run_at);

CREATE TABLE export_runs (
    run_id         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schedule_id    NUMBER       NOT NULL REFERENCES export_schedules (schedule_id),
    status         VARCHAR2(10) NOT NULL CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    started_at     TIMESTAMP    NOT NULL,
    finished_at    TIMESTAMP,
    row_count      NUMBER(10),
    file_name      VARCHAR2(200),              -- name only, never a path; generated by the server
    file_bytes     NUMBER(12),
    error_message  VARCHAR2(1000),
    CONSTRAINT export_runs_file_ck CHECK (status <> 'SUCCESS' OR file_name IS NOT NULL)
);
CREATE INDEX export_runs_schedule_ix ON export_runs (schedule_id, started_at);
```

`params_query` is never empty: the canonical form always contains `sort` and `dir`. That matters because Oracle stores `''` as NULL. Schedules are disabled, never deleted, so run history always keeps its parent row.

**Passwords.**
- Stored as BCrypt hashes with cost 12. The salt is random per password and stored inside the 60-character hash string. At cost 12 a check takes about 0.25 s, which is fine for a login and expensive for anyone brute-forcing a stolen hash.
- Plain SHA-256 would be the wrong choice: it is fast, so billions of guesses per second are possible offline.
- BCrypt ignores input beyond 72 bytes, so validation rejects longer passwords rather than silently truncating them.

**Initial users.**
- `db/02_seed.sql` inserts three demo users, one per role:
  - `viewer` (VIEWER), password `Viewer#2026`
  - `analyst` (ANALYST), password `Analyst#2026`
  - `admin` (ADMIN), password `Admin#2026`
- The seed file holds **only their BCrypt hashes**. I generate them once in M1 with the portal's own `HashPassword` tool (`bin/hash-password.sh`, which reads a password from stdin and prints the hash) and commit them.
- The plain demo passwords appear in the README, because a reviewer needs them to log in.
- To create a real admin or change a password: run `bin/hash-password.sh`, then `UPDATE users SET password_hash = '...' WHERE username = '...'`. There is deliberately no user-management UI (out of scope; see n).

## e. Report registry

**Decision: the report *definitions* live in a Java class, `ReportRegistry`. The *role assignments* live in the database, in `reports` and `report_roles`.**

Why definitions are Java code and not table rows:
- A definition contains SQL: the view name, the column list, and the filter fragments such as `order_date >= ?`, which are pasted into the statement text. If those came from a table, anyone who could `UPDATE` that table could inject SQL, and the sort-column whitelist would be data an attacker could edit.
- As Java constants they go through code review and version control, and a unit test checks every definition (section k).
- They change only when a developer adds a report, which is a code change anyway: it needs a form, labels and formatting.

Why role assignments are rows:
- Who may see a report is a business decision, not code.
- The foreign keys guarantee that only real roles and reports are named.
- Access can be changed with one SQL statement and no rebuild. It is read at startup, so it applies after a restart; see n.

Consistency check: at startup the registry compares the report codes in Java with the rows in `reports`. If they differ in either direction, the web app fails to start, and the Tomcat log names the missing codes. A report can therefore never exist without an access rule.

**A definition contains:**
- code, title, the business question (the warehouse's own comment), and the view (synonym) name;
- the column list, with a display label and a type (TEXT, INTEGER, YEAR, MONEY, PERCENT, DATE, MONTH) used for formatting;
- parameters, each with a name, label, type, required flag, default, limits and SQL fragment;
- cross-field rules (from ≤ to);
- the sortable columns (the whitelist), the default sort, and tie-breaker columns for stable paging;
- paged or not, and an optional chart specification;
- roles: loaded from `report_roles` at startup into an immutable `Set<String>` on the definition.

**Full example** (the same report is used in section f):

```java
static ReportDefinition repeatPurchaseGap() {
    return ReportDefinition.builder("repeat_purchase_gap")
        .title("Repeat purchase gap")
        .question("For customers who ordered more than once, how many days pass between one order and the next?")
        .view("v_rpt_repeat_purchase_gap")
        .column("customer_unique_id",  "Customer",            TEXT)
        .column("order_number",        "Order no.",           INTEGER)
        .column("total_orders",        "Orders in total",     INTEGER)
        .column("order_id",            "Order ID",            TEXT)
        .column("order_date",          "Order date",          DATE)
        .column("previous_order_date", "Previous order",      DATE)
        .column("days_since_previous", "Days since previous", INTEGER)
        .column("next_order_date",     "Next order",          DATE)
        .param(ParamDef.date("from_date", "Order date from", "order_date >= ?"))
        .param(ParamDef.date("to_date",   "Order date to",   "order_date <= ?"))
        .param(ParamDef.integer("min_gap_days", "At least this many days since previous order", 0, 1000,
                                "days_since_previous >= ?"))
        .rangeRule("from_date", "to_date")
        .sortable("order_date", "days_since_previous", "total_orders", "customer_unique_id")
        .defaultSort("order_date", SortDirection.ASC)
        .tieBreaker("order_id")          // makes the order unique, so pages never overlap or skip rows
        .build();
}
```
```sql
-- db/02_seed.sql
INSERT INTO reports (report_code) VALUES ('repeat_purchase_gap');
INSERT INTO report_roles (report_code, role_code) VALUES ('repeat_purchase_gap', 'ANALYST');
INSERT INTO report_roles (report_code, role_code) VALUES ('repeat_purchase_gap', 'ADMIN');
```
`order_date <= ?` is correct for an inclusive end date because the view's dates come from `dim_date.calendar_date`, which are all midnight.

**The 12 reports, their parameters and roles.**
- Every report also takes `sort` (whitelist), `dir` (`asc`/`desc`) and, if paged, `page`.
- Roles: **V** = VIEWER, **A** = ANALYST, **D** = ADMIN. Each role is assigned explicitly per report, with no role hierarchy, so what a role can see is one `SELECT` away.
- The reasoning for the split: viewers get company-level summaries; analysts also get state, category, seller and customer-behaviour detail; only admins see the customer address history.

| Code | View | Parameters (fragment) | Sortable | Chart | Roles |
|---|---|---|---|---|---|
| monthly_revenue | v_rpt_monthly_revenue | from_month MONTH (`month_start >= ?`), to_month MONTH (`month_start <= ?`) | month_start, revenue, order_count, avg_order_value, mom_growth_pct | **line**: revenue by month | V A D |
| revenue_grouping_sets | v_rpt_revenue_grouping_sets | breakdown CHOICE YEAR / PAYMENT TYPE / TOTAL (`breakdown = ?`) | breakdown, year_num, payment_type, revenue, order_count | | V A D |
| weekday_orders | v_rpt_weekday_orders_pivot | none: sort only | day_of_week_num, orders_2017, orders_2018 | | V A D |
| late_delivery | v_rpt_late_delivery_cube | state STATE (`customer_state = ?`), order_year CHOICE 2016/2017/2018 bound as a **string** because the column is VARCHAR2 (`order_year = ?`), hide_subtotals FLAG (`grouping_level = 0`) | customer_state, order_year, late_pct, delivered_orders | | V A D |
| payment_mix | v_rpt_payment_mix_monthly | from_month, to_month (as above) | month_start, total_revenue, credit_card_share_pct, boleto_share_pct | **stacked bar**: 5 payment types by month | A D |
| revenue_state_category | v_rpt_revenue_state_category | state, category CATEGORY (`category = ?`), hide_subtotals FLAG | customer_state, category, revenue, order_count | | A D |
| top_categories_by_state | v_rpt_top_categories_by_state | state, top_n INTEGER 1–3 (`category_rank <= ?`) | customer_state, category_rank, revenue | | A D |
| seller_rank | v_rpt_seller_rank_in_state | seller_state STATE (`seller_state = ?`), top_n INTEGER 1–10 (`seller_rank <= ?`) | seller_state, seller_rank, revenue, state_revenue_share_pct | | A D |
| category_quarters | v_rpt_category_quarter_pivot | category, quarter COLUMN (whitelist of the 8 `rev_*` columns, default 2018 Q2), top_n ROW_LIMIT 1–50 (`FETCH FIRST ? ROWS ONLY`) | fixed: ordered by the chosen quarter, descending | **bar**: top N categories in the chosen quarter | A D |
| cohort_retention | v_rpt_cohort_retention | from_cohort MONTH, to_cohort MONTH (`cohort_month >= ? / <= ?`), max_months INTEGER 0–25 (`months_since_first <= ?`) | cohort_month, months_since_first, retention_pct, cohort_size | | A D |
| repeat_purchase_gap | v_rpt_repeat_purchase_gap | from_date DATE, to_date DATE, min_gap_days INTEGER 0–1000 | see example | | A D |
| customer_moves | v_rpt_customer_moves | to_state STATE (`to_state = ?`), changed_state_only FLAG (`changed_state = 'Y'`) | recorded_at, to_state, from_state, customer_unique_id | | D |

**Where a date range cannot apply, and why.** Several views already sum over all dates before the portal sees them: state × category, top categories, seller rank and the grouping-sets view. The portal can filter the *rows* of a view, but it cannot re-aggregate a view by dates the view no longer has. So those forms offer the filters their columns support (state, category, top-N, year). This is what "as fits the view" means here. Adding a month column to those views would be a warehouse change (question 5).

## f. Parameter handling

**Validation.** `ParamValidator` loops over the report's **defined** parameters and reads each by name. Parameters the report does not define are never read, so they can never reach SQL.

Common rules for every parameter:
- If the value is missing or blank after trimming, the parameter is absent. A required parameter that is absent gets the error "*Label* is required."
- More than 100 characters gives "*Label* is too long." This check runs before any parsing.
- The same parameter given twice gives "*Label* was given more than once."

| Type | Accepted input | Parsed to / bound as | Error message |
|---|---|---|---|
| MONTH | regex `^\d{4}-(0[1-9]|1[0-2])$`, then `YearMonth.parse`, and 2016-01 to 2019-12 (the `dim_date` range) | first day of the month, `java.sql.Date` | "From month must be a month between 2016-01 and 2019-12, written YYYY-MM." |
| DATE | regex `^\d{4}-\d{2}-\d{2}$`, then `LocalDate.parse` with `ISO_LOCAL_DATE`, which is strict (2017-02-30 is rejected), and 2016-01-01 to 2019-12-31 | `java.sql.Date` | "Order date from must be a real date between 2016-01-01 and 2019-12-31, written YYYY-MM-DD." |
| INTEGER / ROW_LIMIT | regex `^\d{1,4}$`, `Integer.parseInt`, and min–max | `Integer` | "Top N must be a whole number from 1 to 10." |
| STATE | trim, upper-case, `^[A-Z]{2}$`, and one of the 27 Brazilian state codes (a constant set) | `String` | "State must be a Brazilian state code such as SP or RJ." |
| CHOICE | exact match against the definition's fixed list | `String` | "Breakdown must be one of: YEAR, PAYMENT TYPE, TOTAL." |
| CATEGORY | exact match against the 74 categories, loaded once at startup with `SELECT DISTINCT category FROM v_rpt_revenue_state_category WHERE grouping_level = 0`. The form shows them as a `<select>`. | `String` | "Category is not a known product category." |
| FLAG | absent → false; `on` or `true` → true; anything else is an error | nothing bound: the fragment is a constant | "Hide subtotals must be on or off." |
| COLUMN | a key in the definition's map, e.g. `2018_Q2` → `rev_2018_q2` | nothing bound: the mapped **constant** identifier is used | "Quarter must be one of: 2017_Q1, …, 2018_Q4." |
| sort | a key in the report's sortable whitelist | the constant column name | "Sort column must be one of: order_date, days_since_previous, …" |
| dir | `asc` or `desc` → `SortDirection` enum | the enum's constant SQL: `ASC NULLS LAST` / `DESC NULLS LAST` | "Direction must be asc or desc." |
| page | INTEGER 1–10,000, default 1 | `OFFSET` bind | "Page must be a whole number from 1 to 10000." |

Cross-field rule: if both ends of a range are present and from > to, the error goes on the "to" field: "To month must not be before From month." All errors are collected, not just the first. The form shows each message next to its field, and the submitted values are redisplayed through `<c:out>`.

**From validated values to SQL.** `ReportQuery` builds the statement in a fixed order. **Every piece of SQL text is a Java constant from the registry.** User input only ever reaches Oracle through `setObject(index, value)`.

```
SELECT <constant column list> FROM <constant view name>
[WHERE <fragment 1> AND <fragment 2> ...]        -- only the fragments whose parameter is present, in definition order
ORDER BY <whitelisted sort column> <enum direction>, <constant tie-breakers> ASC
OFFSET ? ROWS FETCH NEXT ? ROWS ONLY            -- page mode: binds (page-1)*50 and 51
   or FETCH FIRST ? ROWS ONLY                   -- top-N (ROW_LIMIT), chart and export modes
```
Each present non-FLAG parameter adds its fragment and appends its typed value to the bind list, in the same order. The bind count therefore always equals the `?` count, and a unit test asserts that for every report.

**How the sort whitelist works.**
- The request says `sort=days_since_previous`. The validator looks that string up in the report's `Map<String, String>` of sortable keys to column names.
- If the key is there, the query uses **the map's value** (a constant), never the request string.
- If it is not there, the result is a 400 with the message above. `sort=revenue;DROP TABLE users--` is simply "not one of the allowed columns". It never gets near the SQL.
- Direction goes through an enum the same way. Column names cannot be bind variables (`ORDER BY ?` would sort by a constant string), which is exactly why they need a whitelist.

**The SQL for one parameterised report**, generated for the example URL in section c: `from_date=2017-06-01`, `min_gap_days=30`, sort by gap descending, page 2.

```sql
SELECT customer_unique_id, order_number, total_orders, order_id,
       order_date, previous_order_date, days_since_previous, next_order_date
FROM   v_rpt_repeat_purchase_gap
WHERE  order_date >= ?
AND    days_since_previous >= ?
ORDER  BY days_since_previous DESC NULLS LAST, order_id ASC
OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
-- binds: 1 = java.sql.Date 2017-06-01, 2 = Integer 30, 3 = Integer 50, 4 = Integer 51
```
- `to_date` was not given, so its fragment is not included.
- `NULLS LAST` is explicit because Oracle puts NULLs **first** in a descending sort, and a customer's first order has NULL `days_since_previous`.
- Asking for 51 rows tells us whether there is a next page without running a `COUNT(*)` over the whole view. The trade-off: the page shows "Page 2 · Next ›", not "Page 2 of 123".

## g. Security design

**AuthFilter** (mapped to `/*`).
- Public paths pass through untouched: `/login`, `/health` and `/static/*`.
- Every other request needs a session (`getSession(false)`, so a session is never created here) holding `currentUser`, an immutable `AuthenticatedUser(userId, username, displayName, Set<String> roles)`.
- Without one: `/api/*` gets 401 `{"error":"not_authenticated"}`; everything else gets a redirect to `/login`.
- There is deliberately no `?next=` return URL, so there is no open-redirect risk. After login the user always lands on `/reports`.

**Login (LoginServlet POST).**
1. CsrfFilter has already checked the token. The login form carries one too, which prevents *login CSRF*: an attacker cannot log a victim's browser into the attacker's account.
2. Validate `username` (`^[a-z0-9._-]{3,30}$` after lower-casing) and password (1–72 bytes).
3. `UserDao.findActiveByUsername`. If there is no such active user, still run one BCrypt check against a fixed dummy hash. The response then takes the same time whether or not the username exists, so timing does not reveal valid usernames.
4. A wrong password, an unknown user and an inactive account all give the same message: "Invalid username or password."
5. On success:
   - `session.invalidate()` on the pre-login session, then `request.getSession(true)` for a brand-new one. That is the **session-fixation defence**: a session ID an attacker planted before login is worthless after it.
   - Put `currentUser`, with roles loaded from `user_roles`, and a new CSRF token into the new session.
   - Update `last_login_at`.
   - Redirect to `/reports`.
6. Session timeout is 30 minutes of inactivity (`<session-timeout>`).

**Logout** is POST only, with the CSRF token. A logout link as a GET could be triggered by an `<img>` tag on another site. Logout calls `session.invalidate()` and redirects to `/login?loggedOut`.

**Session cookie** (`web.xml` `<session-config>`, standard in Servlet 6.0):
```xml
<cookie-config>
  <http-only>true</http-only>                  <!-- JavaScript cannot read JSESSIONID, so XSS cannot steal it -->
  <secure>true</secure>                        <!-- only sent over HTTPS (browsers and curl treat http://localhost as secure) -->
  <attribute><attribute-name>SameSite</attribute-name><attribute-value>Lax</attribute-value></attribute>
</cookie-config>
<tracking-mode>COOKIE</tracking-mode>          <!-- never ;jsessionid=... in URLs, where it would leak in logs and Referer headers -->
```
- **Lax rather than Strict:** Lax already stops the cookie being sent on cross-site POSTs. Strict would also drop it when a user follows a link to the portal from email or chat, which looks like being logged out.
- **HTTPS:** in a real deployment TLS would end at a reverse proxy or a Tomcat HTTPS connector, and nothing in the app would change.

**CSRF: synchronizer token.**
- One random token per session: 32 bytes from `SecureRandom`, base64url-encoded. It is created when the session is created, and replaced at login.
- Every POST form includes `<t:csrf/>`, a tag file that prints `<input type="hidden" name="_csrf" value="...">`.
- CsrfFilter checks **every** POST: the session must exist and its token must equal `_csrf`. The comparison uses `MessageDigest.isEqual`, which runs in constant time.
- A mismatch gets 403 "This form has expired. Reload the page and try again." and a log line.
- Rule: GET handlers never change state, so they need no token.
- The JSON API is GET only. Another origin cannot read its responses: there is no CORS header, and the Lax cookie is not sent on cross-site `fetch`.

**JSP output escaping.**
- Every value from the database or the request is printed with `<c:out value="${...}"/>`, which escapes `< > & ' "` by default. Inside attributes, `value="${fn:escapeXml(x)}"` is used.
- A bare `${...}` in JSP template text is **not** escaped, so it is never used for data.
- `web.xml` sets `<scripting-invalid>true</scripting-invalid>` for `*.jsp`, so a JSP that contains a scriptlet fails to compile.
- The JSON writer escapes `"`, `\`, control characters, and also `<`, `>` and `&` as `\u003c` etc.
- Chart labels are drawn on a canvas, and JS error lists use `textContent`, never `innerHTML`.

**SecurityHeadersFilter** (defence in depth):
- `Content-Security-Policy: default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`. There are no inline scripts, so an injected `<script>` would not run even if escaping were missed. Page data reaches JS through `data-*` attributes.
- `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, and `Cache-Control: no-store` on authenticated pages.
- Chart.js is pinned to one version, with a Subresource Integrity (`integrity=`) hash.

**Role enforcement points.** They all call one method, `AccessPolicy.canView(AuthenticatedUser user, ReportDefinition report)`, which returns true if `user.roles` and `report.roles` share at least one role.

| # | Where | If refused |
|---|---|---|
| 1 | ReportListServlet: builds the menu | report not listed (this is only a convenience, not the security) |
| 2 | ReportServlet `/reports/{code}` | 403 page |
| 3 | ChartPageServlet `/charts/{code}` | 403 page |
| 4 | ReportDataServlet `/api/reports/{code}` | 403 `{"error":"forbidden"}` |
| 5 | ExportServlet POST `action=create` | 403 page: you cannot schedule what you cannot see |
| 6 | ExportScheduler, **at every run**, with the owner's roles re-read from the database | run marked FAILED: "The schedule owner no longer has access to this report." |
| 7 | ExportDownloadServlet | 404 unless you are the run's owner (or ADMIN) **and** can still view the report |

**What an attacker sees hitting a forbidden report directly.** Logged in as `viewer`, `GET /reports/customer_moves`:
- The response is 403 with the generic `error.jsp`: "You do not have access to this report."
- No parameter is parsed and no SQL runs.
- The server logs `WARN access denied user=viewer report=customer_moves`.
- `GET /api/reports/customer_moves` gets 403 JSON, and `/charts/payment_mix` gets 403.

Unknown codes get 404. Report codes are not secret (they are in the README), so 403 vs 404 reveals nothing useful. **Other users' export runs get 404, not 403**, so run IDs cannot be probed to find out which ones exist.

**Health page.** `/health` is public so a monitor can call it. It shows only:
- `database: UP` (`SELECT 1 FROM DUAL` through the pool);
- `warehouse views: READABLE` (`SELECT COUNT(*) FROM v_rpt_weekday_orders_pivot`, which catches lost grants);
- the time taken.

It never shows versions, URLs or error text. It returns HTTP 503 if either check fails.

## h. Scheduler design

**Start and stop with the servlet context.** `AppContextListener` (`@WebListener`):
- `contextInitialized`:
  1. `AppConfig.fromEnvironment()`, which fails fast with a message naming any missing variable.
  2. Create the `HikariDataSource`.
  3. Load the registry and check it against `reports`.
  4. Mark runs left in `RUNNING` as `FAILED` ("Interrupted: the portal stopped while this export was running").
  5. Create `ExportScheduler` with a single-thread `ScheduledExecutorService` and `scheduleWithFixedDelay(tick, 10 s, 30 s)`.
  6. Store the objects as **application-scope** attributes.
- `contextDestroyed`, in reverse order:
  1. `executor.shutdown()`, then `awaitTermination(30 s)`, then `shutdownNow()`.
  2. Close the pool. The scheduler must stop first, or a running export would lose its connection.
  3. Deregister the Oracle JDBC driver that this web app loaded. Otherwise Tomcat reports a memory leak on redeploy.
- The executor's thread factory names the thread `export-scheduler` and makes it a daemon.

**One tick** (the whole body is inside `try { … } catch (Throwable t) { log }`, because a `ScheduledExecutorService` **silently stops rescheduling** a task that throws):
1. `now` = the JVM's current UTC time, bound as a parameter. The database clock is never used for scheduling decisions.
2. `SELECT schedule_id, next_run_at FROM export_schedules WHERE is_enabled = 'Y' AND next_run_at <= ? ORDER BY next_run_at`
3. For each due schedule, **claim it first** and commit:
   `UPDATE export_schedules SET next_run_at = ? WHERE schedule_id = ? AND next_run_at = ? AND is_enabled = 'Y'`
   - The new value is the next occurrence of `run_time` after `now` in `PORTAL_TIME_ZONE`, computed by `NextRunCalculator` and converted to UTC.
   - Only if the update count is 1 does this tick run the export. This is optimistic locking on `next_run_at`: if two ticks or two Tomcat instances see the same due row, only one update matches and the other gets 0 rows.
   - Within one JVM a single-thread executor also cannot overlap itself.
4. `INSERT export_runs (status 'RUNNING', started_at now)` and commit.
5. Re-check access (point 6 in section g), re-validate the stored `params_query` with the same `ParamValidator`, and build `ReportQuery.forExport` (no paging, `FETCH FIRST 100001 ROWS ONLY`).
6. Stream rows into `CsvWriter`, which writes to `<exportDir>/.tmp-<runId>.csv`. Then `Files.move(..., ATOMIC_MOVE)` to `<reportCode>_<yyyyMMdd_HHmmss>_run<runId>.csv`. A download can therefore never see a half-written file.
7. Update the run to `SUCCESS` with `finished_at`, `row_count`, `file_name` and `file_bytes`, and commit.

**Missed and failed runs.**
- **Catch-up:** if Tomcat was down at 06:30, the schedule is overdue at the next tick, runs **once**, and the claim moves it to tomorrow 06:30. A week of downtime means one catch-up run, not seven.
- **A failed export** (a SQL error, an invalid stored parameter, lost access, more than 100,000 rows, or disk full):
  - the temp file is deleted;
  - the run becomes `FAILED` with a short `error_message` (exception class and message, cut to 1,000 characters; never a stack trace, which goes to the log instead);
  - the schedule simply runs again at its next daily time. There is **no automatic retry**.
- **Too many rows:** exceeding the cap fails the run rather than silently cutting the file short. The largest view has 6,103 rows.

**"Run now".** The owner can press **Run now** on a schedule. It sets `next_run_at = now`, so the normal tick picks it up within 30 seconds, through the same claim and the same code. It is not in your requirement list. I added it so the smoke test and a demo do not have to wait for a clock time (question 7).

**Time.**
- The JVM runs with `-Duser.timezone=UTC`, and every `TIMESTAMP` column holds UTC.
- `PORTAL_TIME_ZONE` (default `UTC`) is used in only two places: turning "daily at 06:30" into the next UTC instant (`ZonedDateTime` handles daylight-saving gaps), and displaying times.

**Where files go.** `PORTAL_EXPORT_DIR` is `/exports` in the container, bind-mounted to `./exports` on the host, which is gitignored.
- File names are built only from the report code, a timestamp and the run ID. No user input appears in a file name.
- Downloads look the run up by numeric ID, check ownership and access, and take `file_name` **from the database**. The path is `exportDir.resolve(fileName).normalize()` and must still start with `exportDir` (defence in depth; the client never supplies a path).
- The response has `Content-Type: text/csv; charset=UTF-8` and `Content-Disposition: attachment; filename="..."`.
- `run=../../etc/passwd` is not an integer and gets 400.
- **CSV format:** RFC 4180, comma-separated, CRLF line ends, UTF-8. A field containing a comma, quote, CR or LF is quoted, with quotes doubled. Column names form the header row, dates are ISO `yyyy-MM-dd`, and numbers use `BigDecimal.toPlainString()`.
- **CSV injection:** a *text* cell starting with `= + - @` or a tab or CR gets a leading `'`, so a spreadsheet does not execute it as a formula. Numeric cells are left alone, because a negative growth figure is legitimately `-5.2`.

**Limitations of an in-app scheduler, stated plainly.** Compared with an external scheduler such as cron, Quartz with a JDBC job store, Oracle `DBMS_SCHEDULER` or an enterprise tool like Control-M:
- It runs only while Tomcat runs. A redeploy interrupts a running export: that run is marked FAILED at the next startup and is not retried.
- Missed runs collapse into a single catch-up run, and there is no calendar beyond "daily at HH:MM".
- One thread means one export at a time. A slow export delays the others; with these view sizes that means seconds.
- It shares the connection pool and CPU with web users. An export holds one of the 5 connections while it runs.
- There is no alerting and no retry policy: a failure is visible only in the exports list and the log.
- With **two Tomcat instances**, the claim `UPDATE` still prevents double runs. But the startup clean-up of `RUNNING` rows would wrongly fail the other instance's in-flight export. That cleanup is only correct for the single instance this project runs.
- `DBMS_SCHEDULER` would run inside the database, but it would write files on the database server with `UTL_FILE`, where Tomcat could not serve them without a shared volume.

## i. Chart design

**Chart pages.** `/charts/{code}` exists for three reports: `monthly_revenue` (line), `category_quarters` (bar) and `payment_mix` (stacked bar).
- ChartPageServlet runs only the access check and renders `chart.jsp`: the same form as the report page (same parameter definitions, so the same fields), plus a `<canvas>`. It runs no query itself.
- `/static/js/charts.js` works as follows:
  - It reads the form with `FormData` and builds a `URLSearchParams` string.
  - It calls `fetch('/api/reports/' + code + '?' + qs, {credentials: 'same-origin'})`.
  - It draws or redraws the Chart.js chart. Labels are strings (`2017-03`), so no date adapter library is needed.
  - On a 400 it lists the returned error messages with `textContent`.
- The chart's shape (type, label column, series columns) is part of the report definition and is sent in the JSON, so it is defined in one place.

**JSON endpoint contract: `GET /api/reports/{code}?<same parameters as the report form>`**
- The same `AccessPolicy.canView` and the same `ParamValidator` as the HTML page, in the same order (404, then 403, then 400).
- `ReportQuery.forChart` has no paging and returns at most 1,000 rows. Row-limit reports use their own `top_n`.
- Every response is `Content-Type: application/json; charset=UTF-8` with `Cache-Control: no-store`. Methods other than GET get 405.

```json
200  {"report":"monthly_revenue",
      "title":"Monthly revenue trend",
      "columns":[{"name":"month_start","label":"Month","type":"MONTH"},
                 {"name":"revenue","label":"Revenue (R$)","type":"MONEY"}, ...],
      "rows":[["2017-01",120312.87, ...], ["2017-02",247303.02, ...]],
      "chart":{"type":"line","labelColumn":"month_start","series":["revenue"],"stacked":false},
      "truncated":false}
400  {"errors":{"to_month":"To month must not be before From month."}}
401  {"error":"not_authenticated"}
403  {"error":"forbidden"}
404  {"error":"unknown_report"}
```
- Numbers are JSON numbers (`BigDecimal.toPlainString()`), dates are ISO strings, and SQL NULL is `null`.
- For `category_quarters`, `chart.series` is the whitelisted column the user picked, e.g. `["rev_2018_q2"]`.
- The JSON is produced by a small, unit-tested `JsonWriter` class (about 60 lines), because the fixed stack has no JSON library (question 2).

## j. Configuration

The web app reads **environment variables only**, through `AppConfig`. Docker Compose loads them from `.env` with `env_file`, so no credential is ever inside the WAR or the image.
- I chose environment variables over a properties file outside the WAR because Compose already passes them, there is no file to mount, and the same WAR runs anywhere unchanged.
- A missing required variable stops the deployment with `Missing environment variable PORTAL_DB_PASSWORD`, rather than failing later at the first query.

`.env.example` (committed; `.env` is gitignored):
```bash
# Copy this file to .env and set the password. .env is gitignored.
# No comments on the same line as a value (Docker would treat them as part of the value).

# --- read by the web app ---
PORTAL_DB_URL=jdbc:oracle:thin:@//oracle:1521/XEPDB1
PORTAL_DB_USER=portal
PORTAL_DB_PASSWORD=ChangeMePortal1
PORTAL_DB_POOL_SIZE=5
PORTAL_EXPORT_DIR=/exports
PORTAL_TIME_ZONE=UTC
PORTAL_SCHEDULER_INTERVAL_SECONDS=30

# --- read only by bin/db-setup.sh and docker-compose.yml ---
ORACLE_CONTAINER=retail-oracle-1
WAREHOUSE_SCHEMA=DW
WAREHOUSE_NETWORK=retail_default
```

**HikariCP settings, and why.**
- `maximumPoolSize=5` and `minimumIdle=5`: a fixed-size pool, as Hikari's documentation recommends. XE uses at most 2 CPU threads, and the usual starting formula is connections ≈ cores × 2 + effective disks, about 5. More connections than the database can execute in parallel only adds queueing inside Oracle.
- `connectionTimeout=5000`: a user waits at most 5 s for a free connection, then gets an error page instead of hanging.
- `maxLifetime=1800000`: connections are replaced every 30 minutes, before any firewall or database idle timeout could cut them.
- `poolName=portal`.
- Every statement also gets `setQueryTimeout(30)`, so one runaway query cannot hold a connection forever.

`docker-compose.yml`, for the portal only. It joins the warehouse's existing network:
```yaml
services:
  portal:
    build: .                                   # multi-stage: Maven builds the WAR, then tomcat:10.1-jdk17 runs it
    env_file: .env
    environment:
      CATALINA_OPTS: "-Duser.timezone=UTC"
    ports: ["127.0.0.1:8080:8080"]             # reachable from this PC only
    volumes: ["./exports:/exports"]
    networks: [warehouse]
networks:
  warehouse:
    external: true
    name: ${WAREHOUSE_NETWORK:-retail_default}
```

## k. Testing plan

**JUnit 5**, in `src/test/java`. No database and no Tomcat are needed: the model classes do not depend on the servlet API.

| Test class | What it proves (examples) |
|---|---|
| `ParamValidatorTest` | each type accepts good input and rejects bad input with the documented message: `2017-13`, `2017-02-30`, `1999-01`, `SP'--`, `sp` (accepted, upper-cased), `11` for a 1–10 top-N, `abc`, over-long input, duplicate values; from > to; required but missing; unknown parameters ignored; all errors collected |
| `ReportQueryTest` | the exact SQL and bind list for the section f example; absent parameters leave no fragment; FLAG adds a fragment with no bind; `sort=revenue;DROP TABLE users` is rejected; the column in `ORDER BY` is the whitelist constant; ROW_LIMIT gives `FETCH FIRST ?` |
| `ReportRegistryTest` | for all 12 reports: unique codes; view names match `^v_rpt_[a-z_]+$`; sortable, tie-breaker and chart columns exist in the column list; each non-FLAG fragment has exactly one `?` and each FLAG fragment none; the default sort is sortable; exactly 3 reports have charts; the Java codes equal the report codes in `db/02_seed.sql` (the file is parsed, so the two cannot drift) |
| `AccessPolicyTest` | a viewer can see `monthly_revenue` but not `payment_mix` or `customer_moves`; an analyst cannot see `customer_moves`; an admin sees all 12; a user with no roles sees nothing; checked against the real registry plus the seed's role rows |
| `CsvWriterTest` | quoting of commas, quotes and newlines; CRLF line ends; NULL is written as an empty field; dates are ISO; numbers are plain (no `1E+3`); `=cmd()` in text becomes `'=cmd()`; `-5.2` as a number is left unchanged |
| `JsonWriterTest` | escaping of `"`, `\`, control characters and `</script>`; NULL; number formatting |
| `NextRunCalculatorTest` | a time later today; a time already passed today, which moves to tomorrow; exactly now; `Asia/Kolkata`; a daylight-saving gap in `America/Sao_Paulo`, using 2018 dates when Brazil still had DST |
| `ExportFilesTest` | the download path check rejects `../x.csv` and absolute paths; generated file names match `^[a-z_]+_\d{8}_\d{6}_run\d+\.csv$` |

Run with `bin/mvn.sh test`, which runs Maven in a container (section l).

**Integration smoke script: `tests/smoke.sh`** (Bash and curl, run from Git Bash against `http://localhost:8080`). It prints one `PASS`/`FAIL` line per check and exits non-zero if any check fails.
1. `/health` → 200 and `UP`.
2. `/reports` with no session → 302 to `/login`. `/api/reports/monthly_revenue` with no session → 401.
3. GET `/login`, take the `_csrf` token from the HTML, POST the login as `viewer`. Expect a 302, and the `Set-Cookie` line must show `HttpOnly`, `Secure` and `SameSite=Lax`. Expect a **new** session ID compared with the pre-login one (fixation defence).
4. POST login without a CSRF token → 403.
5. `/reports/monthly_revenue?from_month=2017-01&to_month=2017-03` → 200. The table has 3 data rows, including `2017-01`.
6. `/reports/customer_moves` → **403**. `/api/reports/payment_mix` → **403** JSON.
7. `from_month=2017-13` → 400 with its message. `sort=revenue;DROP` → 400. `from_month=<script>` → the page contains `&lt;script&gt;` and never `<script>` (reflected XSS check).
8. `/api/reports/monthly_revenue?from_month=2017-01&to_month=2017-12` → 200 JSON with 12 rows.
9. Log in as `analyst`, then POST `/exports` with `action=create` for `repeat_purchase_gap` with parameters and a run time, then `action=runNow`. Poll `/exports` for up to 90 s until the run shows `SUCCESS`.
10. `/exports/download?run=<id>` → 200 `text/csv`. Print the header line and first 2 rows. The row count equals the one in the list.
11. As `viewer`, download the analyst's run → 404. `run=../../etc/passwd` → 400.
12. POST `/logout` with the token, then use the old cookie on `/reports` → 302 to `/login`.

## l. Repository tree and Maven layout

The repository is named `reporting-portal`, created as a new git repo in `C:\Users\shree\code\portal`, the same arrangement as `retail`. Commits use your repo-local identity (below).

```
portal/                                   (git repo "reporting-portal")
├── DESIGN.md  README.md                  (DEFENSE.md is written locally but gitignored)
├── .env.example  .gitignore  .gitattributes   (LF for *.sh, so Bash in containers works)
├── pom.xml
├── Dockerfile                            stage 1 maven:3.9-eclipse-temurin-17 → mvn package; stage 2 tomcat:10.1-jdk17-temurin
├── docker-compose.yml
├── bin/
│   ├── db-setup.sh                       create PORTAL user, grants, synonyms, schema, seed   (--grants, --reset)
│   ├── mvn.sh                            run Maven in a container (cached ~/.m2 volume), e.g. bin/mvn.sh test
│   └── hash-password.sh                  print a BCrypt hash for a password read from stdin
├── db/
│   ├── 00_create_user.sql                as SYSDBA: user PORTAL and its system privileges
│   ├── 00_grants.sql                     as SYSDBA: SELECT on the 12 views + synonyms (re-runnable)
│   ├── 01_schema.sql                     as PORTAL: tables
│   ├── 02_seed.sql                       as PORTAL: roles, 3 users, 12 reports, access rules
│   └── 99_drop_user.sql                  as SYSDBA: used by --reset
├── exports/                              gitignored; bind-mounted at /exports
├── tests/
│   └── smoke.sh
└── src/
    ├── main/java/com/reportingportal/
    │   ├── config/     AppConfig, AppContextListener, HikariFactory
    │   ├── auth/       AuthFilter, CsrfFilter, SecurityHeadersFilter, LoginServlet, LogoutServlet,
    │   │               AuthenticatedUser, UserDao, PasswordHasher, HashPassword (CLI main)
    │   ├── report/     ReportDefinition, ParamDef, ParamType, ColumnDef, ChartSpec, SortDirection,
    │   │               ReportRegistry, BrazilStates, ParamValidator, ValidatedParams, ValidationResult,
    │   │               ReportQuery, ReportDao, ReportResult, AccessPolicy,
    │   │               ReportListServlet, ReportServlet, ChartPageServlet, ReportDataServlet, JsonWriter
    │   ├── export/     ExportServlet, ExportDownloadServlet, ExportScheduler, NextRunCalculator,
    │   │               ScheduleDao, ExportRunDao, CsvWriter, ExportFiles
    │   └── web/        HealthServlet
    ├── main/webapp/
    │   ├── index.jsp
    │   ├── static/css/portal.css   static/js/charts.js
    │   └── WEB-INF/
    │       ├── web.xml                   filters (ordered), session/cookie config, error pages, scripting-invalid
    │       ├── tags/layout.tag  tags/csrf.tag
    │       └── jsp/login.jsp reports.jsp report.jsp chart.jsp exports.jsp health.jsp error.jsp
    └── test/java/com/reportingportal/    the 8 test classes in section k
```

**Maven: one module, `war` packaging.** Splitting into modules (core, web) would only pay off if another application reused the model classes, and none does.

| Dependency | Scope | Why |
|---|---|---|
| `jakarta.servlet:jakarta.servlet-api` 6.0 | provided | Servlet API. Tomcat 10.1 supplies it at runtime. |
| `jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api` 3.0 + `org.glassfish.web:jakarta.servlet.jsp.jstl` 3.0 | compile | JSTL. Tomcat does **not** include a JSTL implementation, so the WAR must carry one. |
| `com.oracle.database.jdbc:ojdbc11` (21.x, matching the 21c server) | compile | The Oracle JDBC thin driver |
| `com.zaxxer:HikariCP` 5.x | compile | The connection pool. It needs `slf4j-api`, which it brings transitively. |
| `org.slf4j:slf4j-jdk14` | runtime | Routes Hikari's log lines to java.util.logging, and so to Tomcat's log. The app itself logs with java.util.logging from the JDK, so no logging framework is added. |
| `at.favre.lib:bcrypt` 0.10 | compile | BCrypt. It is maintained, and rejects over-long passwords instead of silently truncating them. The classic alternative is `org.mindrot:jbcrypt` 0.4, last released in 2015. |
| `org.junit.jupiter:junit-jupiter` 5.x | test | Tests |

- Build plugins: `maven-compiler-plugin` (`release 17`), `maven-war-plugin`, `maven-surefire-plugin` 3.x, and `exec-maven-plugin` (only for `bin/hash-password.sh`).
- Exact versions will be pinned in M1 against Maven Central. Chart.js 4.x is pinned by URL and SRI hash.

**Build and run.**
- Nothing is installed on Windows. `docker compose up -d --build` compiles and tests the WAR in a Maven container, then copies it into `tomcat:10.1-jdk17-temurin` as `ROOT.war`.
- `bin/mvn.sh test` runs the unit tests alone.
- The JDBC driver goes in `WEB-INF/lib`, not in Tomcat's `lib`, because the app builds its own pool. There is no JNDI DataSource. That is why the listener must deregister the driver at shutdown.

## m. Milestones and checkpoints

At every checkpoint I show real output: command output, curl output or test output. After each milestone I commit with a plain message, using the repo-local identity **Shreecharan Hegde \<shreecharan707@gmail.com\>** (the one `retail` uses). There is no co-author line and no push.

| # | Build | Checkpoint shown to you |
|---|---|---|
| M1 | `git init` + local identity; `.gitignore` (`.env`, `exports/`, `target/`, `DEFENSE.md`); `pom.xml`; Dockerfile; compose; `AppConfig`; `AppContextListener` with HikariCP; `/health`; `db/01_schema.sql`, `db/02_seed.sql` with 3 users; `bin/db-setup.sh`; `bin/mvn.sh`; `bin/hash-password.sh` | `db-setup.sh` output; `SELECT` of users and roles; `docker compose up -d --build` log; `curl -i localhost:8080/health` showing 200 `UP`/`READABLE`; pool start and stop lines in the Tomcat log |
| M2 | Login/logout, AuthFilter, CsrfFilter, SecurityHeadersFilter, `web.xml` session and cookie config, `layout.tag`, `csrf.tag`, `error.jsp` | curl: redirect to login, login with token (cookie flags and a changed session ID), POST without token gives 403, logout |
| M3 | ReportRegistry (12 definitions), the startup consistency check, AccessPolicy, the report list page | the list page as each of the 3 users (4 / 11 / 12 reports); a startup failure shown when a `reports` row is missing |
| M4 | ParamValidator, ReportQuery, ReportDao, ReportServlet, `report.jsp` with sorting and paging | curl on several reports; validation error pages; 403 on a forbidden report; the SQL + binds logged at FINE for the section f example |
| M5 | ReportDataServlet, JsonWriter, ChartPageServlet, `chart.jsp`, `charts.js`; the three charts | JSON from curl (200/400/403); I'll describe what I can verify, and **you check the three charts in a browser**, since I cannot see the rendered canvas |
| M6 | Schedule form on report pages, `/exports`, ExportScheduler, CsvWriter, download servlet | create + run now via curl; `export_runs` rows; the file on disk; a download; a forced failure recorded as FAILED; a Tomcat restart showing catch-up behaviour |
| M7 | All JUnit tests, `tests/smoke.sh` | full `bin/mvn.sh test` output and full `tests/smoke.sh` output |
| M8 | README: what it is, diagram, 5-command run, the 3 logins, reports × roles × parameters table, screenshot placeholders | README steps re-run from a fresh clone |
| Phase 3 | `DEFENSE.md` (gitignored): at least 30 Q&A pairs, plus a line-by-line walkthrough of AuthFilter and ReportServlet | the file, for your review |

## n. Risks and open questions

**Questions for you** (my recommendation first):
1. **Toolchain.** This PC has no JDK or Maven.
   - **Recommended:** build and test everything in Docker (`Dockerfile` stage 1, `bin/mvn.sh`). Nothing gets installed, and it matches `retail`'s "only Docker needed" setup.
   - **Alternative:** install Temurin 17 and Maven locally with winget, so you can run tests inside an IDE. IntelliJ can also download a JDK on its own.
2. **Libraries the fixed stack implies but does not name:** the JSTL implementation (GlassFish; Tomcat has none), `slf4j-jdk14` (for Hikari's logging), the bcrypt library choice (`at.favre.lib:bcrypt` vs `jbcrypt`), and `exec-maven-plugin`. **For JSON I recommend a small hand-written `JsonWriter`** rather than adding Gson or Jackson. OK?
3. **Role split** as in the table in section e: viewer 4 reports, analyst 11, admin 12. OK?
4. **Demo passwords** (`Viewer#2026`, …) appear in the README, and their hashes are committed in the seed. That is normal for a demo; the README will say to change them anywhere shared. OK?
5. **Date filters** apply only where the view keeps a date column (monthly revenue, payment mix, cohort, repeat gap; year on late delivery). The all-time aggregate views get state, category and top-N filters instead. Giving them date ranges would mean changing the warehouse views, which is a separate project. OK to accept as "parameters as fit the view"?
6. **Default time zone** for schedules: `UTC` (matches `retail`'s `.env`) or `Asia/Kolkata`? It is a single setting either way.
7. **"Run now" button:** not in your list. I would add it so the smoke test and demos do not wait for the clock. It uses exactly the same scheduler path. Keep it?
8. **Folder name:** the repo is called `reporting-portal` but lives in `portal/`, as `retail-warehouse` lives in `retail/`. OK, or rename the folder?

**Risks:**
- **Coupling to the warehouse's Docker setup.** The portal expects the container `retail-oracle-1` and the network `retail_default`. Both are configurable in `.env`. If the warehouse is not running, `docker compose up` fails at once with "network retail_default … not found".
- **Warehouse resets.**
  - `install.sh --reset` (run by the warehouse tests) drops the views, and their grants with them. Fix: `bin/db-setup.sh --grants`. `/health` shows `NOT READABLE` until then.
  - `docker compose down -v` in `retail` deletes the database volume, and with it the `PORTAL` schema. Fix: `bin/db-setup.sh`.
- **Live queries.** Each page view re-runs its view's SQL. That was measured at 0.2 s or less, and it is fine for a demo. At real scale, the views would become materialized views refreshed by the nightly load.
- **Secure cookie over plain HTTP.** This works only for `localhost` and `127.0.0.1`. Opening the portal from another machine over `http://<LAN IP>` would make login appear to fail. That is intended, since the port is bound to 127.0.0.1 anyway.
- **Stale access data.**
  - A user's roles are read at login, so a change applies at their next login.
  - Report-to-role rows are read at startup, so a change applies after a restart.
  - The scheduler re-reads the owner's roles on every run.
- **No login rate limiting or lockout.** This is out of scope for a portfolio project. The fix would be a failed-attempts counter on `users` with a timed lockout. BCrypt's cost already slows online guessing to about 4 tries per second per request thread.
- **No user-management UI.** Users are created with SQL plus `bin/hash-password.sh`.
- **Export files are never deleted.** A retention clean-up (e.g. delete after 30 days) would be the next feature.
- **CSV and Excel:** UTF-8 without a BOM. Excel may misread accented characters, but Olist city names are almost all plain ASCII.
- **Chart.js from a CDN.** Chart pages need internet access. Everything else works offline.

### Changes made during the build
- **M1:** the JSTL API jar pulled `jakarta.el-api` into `WEB-INF/lib`. Tomcat already provides that API, so the pom excludes it.
- **M1:** JSPs create an HTTP session by default. Every JSP now declares `session="false"`, and **LoginServlet is the only code that creates a session**.
  - AuthFilter copies `currentUser` into request scope, and CsrfFilter copies `csrfToken` into request scope. Pages read both from there.
  - So `/health`, the error page and static files never hand out a session cookie.
- **M2:** Tomcat 10.1 ships Expression Language 5.0, which finds JavaBean getters (`getDisplayName()`) but not record accessors (`displayName()`). Records are only supported from EL 6.0 (Tomcat 11). So objects that JSPs read, such as `AuthenticatedUser`, are ordinary classes with getters. Records are still used where no JSP reads them (`AppConfig`, `UserDao.StoredUser`).
- **M3:** there is a column type `YEAR` (no thousands separator, so 2017 is not shown as "2,017"). The planned `DECIMAL` type was dropped because no column needs it.

### Decisions (approved 2026-09-26)
1. Build and test in Docker only (`Dockerfile` stage 1 and `bin/mvn.sh`). Nothing is installed on Windows.
2. Libraries as listed in section l, including the GlassFish JSTL implementation, `slf4j-jdk14`, `at.favre.lib:bcrypt` and `exec-maven-plugin`. JSON comes from a hand-written `JsonWriter`.
3. Roles: viewer 4 reports, analyst 11, admin 12 (section e).
4. Demo passwords go in the README, and only their hashes are committed in `db/02_seed.sql`.
5. Date filters only where the view keeps a date column. The other views get state, category and top-N filters.
6. `PORTAL_TIME_ZONE=UTC`.
7. The "Run now" button is included.
8. The repo `reporting-portal` lives in the `portal/` folder.
