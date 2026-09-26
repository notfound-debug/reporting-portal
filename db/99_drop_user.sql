-- =============================================================================
-- 99_drop_user.sql   (run by bin/db-setup.sh --reset as SYSDBA inside XEPDB1)
-- Drops the PORTAL user and everything it owns. Users, schedules and export
-- history are lost; the warehouse (DW) is not touched.
-- ORA-01918 "user does not exist" is ignored so a reset also works the first time.
-- =============================================================================
BEGIN
    EXECUTE IMMEDIATE 'DROP USER &portal_user CASCADE';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1918 THEN
            RAISE;
        END IF;
END;
/
