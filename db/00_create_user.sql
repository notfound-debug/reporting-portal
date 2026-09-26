-- =============================================================================
-- 00_create_user.sql   (run by bin/db-setup.sh as SYSDBA inside XEPDB1)
-- Creates the PORTAL schema user with only the privileges it needs.
-- &portal_user and &portal_password are sqlplus substitution variables that
-- bin/db-setup.sh DEFINEs from .env; they are never stored in this file.
-- =============================================================================

CREATE USER &portal_user IDENTIFIED BY "&portal_password"
    DEFAULT TABLESPACE users
    QUOTA 50M ON users;

-- Log in, and create its own tables. CREATE SEQUENCE is needed because every
-- identity column is backed by a sequence that Oracle creates behind the scenes.
-- No CREATE VIEW, no CREATE PROCEDURE, no DBA role: the portal needs none of them.
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO &portal_user;
