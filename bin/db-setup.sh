#!/usr/bin/env bash
# Create the portal's database schema inside the warehouse's Oracle container.
# Run from Git Bash on the host, after the warehouse is up (../retail: ./bin/up.sh).
#
#   bin/db-setup.sh            create user PORTAL, grants and synonyms, tables, seed data
#   bin/db-setup.sh --grants   only re-apply grants and synonyms on the warehouse views
#                              (needed after the warehouse's install.sh --reset)
#   bin/db-setup.sh --reset    drop user PORTAL (ALL PORTAL DATA IS LOST), then full setup
#
# SQL is piped into sqlplus inside the Oracle container over standard input,
# so passwords never appear on a command line. The SYSDBA steps log in with
# "CONNECT / AS SYSDBA": operating-system authentication, which works because
# the container runs as the "oracle" user in the "dba" group. No SYS password needed.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # stop Git Bash on Windows rewriting /paths inside docker arguments

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    echo "ERROR: .env not found. Run: cp .env.example .env   (then set PORTAL_DB_PASSWORD)"
    exit 1
fi
# Load settings. tr removes Windows line endings in case .env was edited in Notepad.
set -a
source <(tr -d '\r' < .env)
set +a

mode="${1:-full}"
case "$mode" in
    full|--grants|--reset) ;;
    *) echo "Usage: bin/db-setup.sh [--grants | --reset]"; exit 4 ;;
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
        echo "DEFINE dw_schema = ${WAREHOUSE_SCHEMA}"
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

if [ "$mode" = "--grants" ]; then
    echo "== Re-applying grants and synonyms on ${WAREHOUSE_SCHEMA}'s report views"
    run_as_sysdba db/00_grants.sql
    echo "Done."
    exit 0
fi

if [ "$mode" = "--reset" ]; then
    echo "== Dropping user ${PORTAL_DB_USER}"
    run_as_sysdba db/99_drop_user.sql
fi

echo "== Creating user ${PORTAL_DB_USER}, grants and synonyms (as SYSDBA)"
if ! run_as_sysdba db/00_create_user.sql db/00_grants.sql; then
    echo "ERROR: setup failed. If the user already exists, use --grants or --reset."
    exit 1
fi

echo "== Creating tables and seed data (as ${PORTAL_DB_USER})"
run_as_portal db/01_schema.sql db/02_seed.sql
echo "Done."
