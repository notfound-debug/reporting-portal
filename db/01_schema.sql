-- =============================================================================
-- 01_schema.sql   (run by bin/db-setup.sh as the PORTAL user)
-- The portal's own tables: who can log in, which roles they have, which roles
-- may see which report, and the scheduled CSV exports with their run history.
--
-- Keys: identity columns (GENERATED ALWAYS AS IDENTITY). The warehouse uses
-- sequences because it inserts fixed -1 "Unknown" rows; the portal has no such
-- rows, so identity columns are the simpler choice here.
-- Every TIMESTAMP column holds UTC. The app runs with -Duser.timezone=UTC and
-- the defaults below use SYS_EXTRACT_UTC.
-- =============================================================================

CREATE TABLE roles (
    role_code    VARCHAR2(20)  CONSTRAINT roles_pk PRIMARY KEY,
    description  VARCHAR2(200) NOT NULL
);
COMMENT ON TABLE roles IS 'The three portal roles: VIEWER, ANALYST, ADMIN.';

CREATE TABLE users (
    user_id        NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT users_pk PRIMARY KEY,
    username       VARCHAR2(30)  NOT NULL
                   CONSTRAINT users_username_uk UNIQUE
                   CONSTRAINT users_username_ck CHECK (REGEXP_LIKE(username, '^[a-z0-9._-]{3,30}$')),
    password_hash  VARCHAR2(60)  NOT NULL,
    display_name   VARCHAR2(100) NOT NULL,
    is_active      CHAR(1) DEFAULT 'Y' NOT NULL CONSTRAINT users_active_ck CHECK (is_active IN ('Y', 'N')),
    created_at     TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
    last_login_at  TIMESTAMP
);
COMMENT ON TABLE users IS 'Portal logins. password_hash is a BCrypt hash (cost 12, salt included); plain passwords are never stored.';

CREATE TABLE user_roles (
    user_id    NUMBER       NOT NULL CONSTRAINT user_roles_user_fk REFERENCES users (user_id),
    role_code  VARCHAR2(20) NOT NULL CONSTRAINT user_roles_role_fk REFERENCES roles (role_code),
    CONSTRAINT user_roles_pk PRIMARY KEY (user_id, role_code)
);
COMMENT ON TABLE user_roles IS 'Which roles each user has. Read at login into the session.';

-- The report definitions themselves (view, columns, parameters, SQL fragments)
-- are Java code in ReportRegistry. This table exists so that report_roles and
-- export_schedules can only ever name a real report (foreign keys), and the
-- app refuses to start if this list and the Java list differ.
CREATE TABLE reports (
    report_code  VARCHAR2(40) CONSTRAINT reports_pk PRIMARY KEY
                 CONSTRAINT reports_code_ck CHECK (REGEXP_LIKE(report_code, '^[a-z][a-z0-9_]{2,39}$'))
);
COMMENT ON TABLE reports IS 'One row per report defined in the Java ReportRegistry; the foreign-key target for access rules and schedules.';

CREATE TABLE report_roles (
    report_code  VARCHAR2(40) NOT NULL CONSTRAINT report_roles_report_fk REFERENCES reports (report_code),
    role_code    VARCHAR2(20) NOT NULL CONSTRAINT report_roles_role_fk REFERENCES roles (role_code),
    CONSTRAINT report_roles_pk PRIMARY KEY (report_code, role_code)
);
COMMENT ON TABLE report_roles IS 'Which roles may see which report. Read at startup; a user may see a report if they hold any of its roles.';

CREATE TABLE export_schedules (
    schedule_id   NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT export_schedules_pk PRIMARY KEY,
    user_id       NUMBER         NOT NULL CONSTRAINT export_schedules_user_fk REFERENCES users (user_id),
    report_code   VARCHAR2(40)   NOT NULL CONSTRAINT export_schedules_report_fk REFERENCES reports (report_code),
    -- Canonical query string of already-validated parameters, re-validated at every run.
    -- Never empty (it always has sort and dir), which matters because Oracle stores '' as NULL.
    params_query  VARCHAR2(1000) NOT NULL,
    -- HH:MM, in the portal's time zone (PORTAL_TIME_ZONE).
    run_time      VARCHAR2(5)    NOT NULL
                  CONSTRAINT export_schedules_time_ck CHECK (REGEXP_LIKE(run_time, '^([01][0-9]|2[0-3]):[0-5][0-9]$')),
    -- UTC. The scheduler claims a due schedule by moving this forward (see ExportScheduler).
    next_run_at   TIMESTAMP      NOT NULL,
    -- Schedules are disabled, never deleted, so their run history keeps its parent row.
    is_enabled    CHAR(1) DEFAULT 'Y' NOT NULL CONSTRAINT export_schedules_enabled_ck CHECK (is_enabled IN ('Y', 'N')),
    created_at    TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL
);
COMMENT ON TABLE export_schedules IS 'Daily CSV exports: which report, with which parameters, at what time, for which user.';

-- The scheduler asks "which enabled schedules are due?" every 30 seconds.
CREATE INDEX export_schedules_due_ix ON export_schedules (is_enabled, next_run_at);
CREATE INDEX export_schedules_user_ix ON export_schedules (user_id);

CREATE TABLE export_runs (
    run_id         NUMBER GENERATED ALWAYS AS IDENTITY CONSTRAINT export_runs_pk PRIMARY KEY,
    schedule_id    NUMBER       NOT NULL CONSTRAINT export_runs_schedule_fk REFERENCES export_schedules (schedule_id),
    status         VARCHAR2(10) NOT NULL CONSTRAINT export_runs_status_ck CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    started_at     TIMESTAMP    NOT NULL,
    finished_at    TIMESTAMP,
    row_count      NUMBER(10),
    -- A file name only, never a path, generated by the server from the report code,
    -- a timestamp and the run id. Downloads read it from here, not from the request.
    file_name      VARCHAR2(200),
    file_bytes     NUMBER(12),
    error_message  VARCHAR2(1000),
    CONSTRAINT export_runs_file_ck CHECK (status <> 'SUCCESS' OR file_name IS NOT NULL)
);
COMMENT ON TABLE export_runs IS 'One row per export attempt: RUNNING while it runs, then SUCCESS with the file or FAILED with the reason.';

CREATE INDEX export_runs_schedule_ix ON export_runs (schedule_id, started_at);
