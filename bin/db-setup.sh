#!/usr/bin/env bash
# Create the portal's own schema (user PORTAL) in the warehouse's Oracle database.
# Run from Git Bash on the host while the warehouse's Oracle container is running.
#
#   bin/db-setup.sh            create user PORTAL, its tables and seed data
#   bin/db-setup.sh --reset    drop user PORTAL first (ALL PORTAL DATA IS LOST)
#
# This touches nothing of the warehouse: no DW object, grant or file. The portal
# reads the report views through the warehouse's own read-only reporting user
# (WAREHOUSE_DB_USER in .env), which the warehouse creates and maintains.
#
# SQL is piped into sqlplus inside the Oracle container over standard input,
# so passwords never appear on a command line. Creating a user needs SYSDBA:
# "CONNECT / AS SYSDBA" uses operating-system authentication, which works because
# the container runs as the "oracle" user in the "dba" group. No SYS password needed.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # stop Git Bash on Windows rewriting /paths inside docker arguments

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    echo "ERROR: .env not found. Run: cp .env.example .env   (then set the passwords)"
    exit 1
fi
# Load settings. tr removes Windows line endings in case .env was edited in Notepad.
set -a
source <(tr -d '\r' < .env)
set +a

mode="${1:-full}"
case "$mode" in
    full|--reset) ;;
    *) echo "Usage: bin/db-setup.sh [--reset]"; exit 4 ;;
esac

# Run the given SQL files as SYSDBA in the pluggable database XEPDB1.
# VERIFY OFF stops sqlplus from echoing lines after variable substitution,
# which would print the password.
run_as_sysdba() {
    {
        echo "WHENEVER SQLERROR EXIT FAILURE ROLLBACK"
        echo "CONNECT / AS SYSDBA"
        echo "ALTER SESSION SET CONTAINER = XEPDB1;"
        echo "SET VERIFY OFF FEEDBACK ON"
        echo "DEFINE portal_user = ${PORTAL_DB_USER}"
        echo "DEFINE portal_password = ${PORTAL_DB_PASSWORD}"
        for file in "$@"; do cat "$file"; echo; done
        echo "EXIT"
    } | docker exec -i "$ORACLE_CONTAINER" sqlplus -s -L /nolog
}

# Run the given SQL files as the PORTAL user. DEFINE OFF: no & substitution
# in these files, so an & inside data can never be misread as a variable.
run_as_portal() {
    {
        echo "WHENEVER SQLERROR EXIT FAILURE ROLLBACK"
        echo "CONNECT ${PORTAL_DB_USER}/\"${PORTAL_DB_PASSWORD}\"@//localhost:1521/XEPDB1"
        echo "SET DEFINE OFF FEEDBACK ON"
        for file in "$@"; do cat "$file"; echo; done
        echo "EXIT"
    } | docker exec -i "$ORACLE_CONTAINER" sqlplus -s -L /nolog
}

if [ "$mode" = "--reset" ]; then
    # Oracle cannot drop a user that has open sessions, and the running portal
    # keeps pooled connections open. Stop it first (this repo's container only);
    # start it again afterwards with: docker compose up -d
    echo "== Stopping the portal container (if running)"
    docker compose stop portal > /dev/null 2>&1 || true
    echo "== Dropping user ${PORTAL_DB_USER}"
    run_as_sysdba db/99_drop_user.sql
fi

echo "== Creating user ${PORTAL_DB_USER} (as SYSDBA)"
if ! run_as_sysdba db/00_create_user.sql; then
    echo "ERROR: setup failed. If the user already exists, use --reset."
    exit 1
fi

echo "== Creating tables and seed data (as ${PORTAL_DB_USER})"
run_as_portal db/01_schema.sql db/02_seed.sql
echo "Done."
