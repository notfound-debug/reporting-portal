#!/usr/bin/env bash
# End-to-end smoke test against the running portal, using only curl and grep.
# Run from Git Bash (or any Bash) after `docker compose up -d --build`:
#
#   tests/smoke.sh                      against http://localhost:8080
#   PORTAL_URL=http://host:8080 tests/smoke.sh
#
# It logs in as the seeded users, runs reports, checks that forbidden reports are
# refused, tries injection and traversal inputs, schedules an export, runs it,
# downloads it, and cleans up (disables the schedule it created).
# Prints one PASS/FAIL line per check and exits 1 if any check failed.
set -uo pipefail

BASE="${PORTAL_URL:-http://localhost:8080}"
VIEWER_PASSWORD="${VIEWER_PASSWORD:-Viewer#2026}"
ANALYST_PASSWORD="${ANALYST_PASSWORD:-Analyst#2026}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

passed=0
failed=0

# check "<description>" <command...>: runs the command; exit code 0 = PASS.
check() {
    local description="$1"; shift
    if "$@"; then
        echo "PASS  $description"; passed=$((passed + 1))
    else
        echo "FAIL  $description"; failed=$((failed + 1))
    fi
}

# request <curl args...>: runs curl, keeps the body and headers, sets $code.
request() {
    code="$(curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' "$@")"
}

# expect <status> [<text in body>] [<text in headers>]: tests the last request.
expect() {
    [ "$code" = "$1" ] || { echo "      got HTTP $code, expected $1"; return 1; }
    if [ -n "${2:-}" ] && ! grep -q -- "$2" "$WORK/body"; then echo "      body lacks: $2"; return 1; fi
    if [ -n "${3:-}" ] && ! grep -qi -- "$3" "$WORK/headers"; then echo "      headers lack: $3"; return 1; fi
    return 0
}

body_lacks() { ! grep -q -- "$1" "$WORK/body"; }

csrf_token() {    # the hidden _csrf value in a saved page
    grep -o 'name="_csrf" value="[^"]*"' "$1" | head -1 | sed 's/.*value="//; s/"$//'
}

session_id() {    # the JSESSIONID in a cookie jar
    grep JSESSIONID "$1" | awk '{print $7}'
}

login() {         # login <username> <password> <cookie jar>; sets $code
    rm -f "$3"
    curl -s -c "$3" -o "$WORK/login.html" "$BASE/login"
    request -b "$3" -c "$3" --data-urlencode "_csrf=$(csrf_token "$WORK/login.html")" \
        --data-urlencode "username=$1" --data-urlencode "password=$2" "$BASE/login"
}

echo "Smoke test against $BASE"
echo

# ------------------------------------------------------------------ health and login
request "$BASE/health"
check "health page is 200 and UP"                             expect 200 'status: UP'
request "$BASE/reports"
check "report list without a session redirects to /login"     expect 302 '' '^Location: /login'
request "$BASE/api/reports/monthly_revenue"
check "JSON endpoint without a session is 401"                expect 401 'not_authenticated'

curl -s -c "$WORK/viewer.jar" -o /dev/null "$BASE/login"
pre_login_session="$(session_id "$WORK/viewer.jar")"
request -b "$WORK/viewer.jar" -d 'username=viewer' --data-urlencode "password=$VIEWER_PASSWORD" "$BASE/login"
check "login POST without a CSRF token is 403"                expect 403

login viewer wrong-password "$WORK/viewer.jar"
check "wrong password is refused with a generic message"      expect 401 'Invalid username or password.'

login viewer "$VIEWER_PASSWORD" "$WORK/viewer.jar"
check "viewer logs in (302 to /reports)"                      expect 302 '' '^Location: /reports'
grep -i '^Set-Cookie: JSESSIONID' "$WORK/headers" | tr -d '\r' | sed 's/^/      /'
check "session cookie is HttpOnly"                            grep -qi '^Set-Cookie: JSESSIONID=.*HttpOnly' "$WORK/headers"
check "session cookie is Secure"                              grep -qi '^Set-Cookie: JSESSIONID=.*Secure' "$WORK/headers"
check "session cookie is SameSite=Lax"                        grep -qi '^Set-Cookie: JSESSIONID=.*SameSite=Lax' "$WORK/headers"
check "session id changes at login (fixation defence)"        test "$(session_id "$WORK/viewer.jar")" != "$pre_login_session"

# ------------------------------------------------------------------ reports as viewer
request -b "$WORK/viewer.jar" "$BASE/reports/monthly_revenue?from_month=2017-01&to_month=2017-03"
check "viewer runs monthly_revenue 2017-01..03: 3 rows"       expect 200 'Rows 1–3 '
check "  ...the first row is 2017-01"                         grep -q '<td>2017-01</td>' "$WORK/body"

request -b "$WORK/viewer.jar" "$BASE/reports/customer_moves"
check "viewer is refused customer_moves (403)"                expect 403 'You do not have access to this report.'
request -b "$WORK/viewer.jar" "$BASE/reports/payment_mix?from_month=2017-01"
check "viewer is refused payment_mix, even with parameters"   expect 403
request -b "$WORK/viewer.jar" "$BASE/api/reports/payment_mix"
check "viewer is refused payment_mix JSON"                    expect 403 '"error":"forbidden"'
request -b "$WORK/viewer.jar" "$BASE/charts/category_quarters"
check "viewer is refused the category_quarters chart"         expect 403

request -b "$WORK/viewer.jar" "$BASE/reports/monthly_revenue?from_month=2017-13"
check "invalid month is 400 with a clear message"             expect 400 'From month must be a month between 2016-01 and 2019-12'
request -b "$WORK/viewer.jar" "$BASE/reports/monthly_revenue?sort=revenue;DROP%20TABLE%20users--"
check "SQL in the sort parameter is 400 (whitelist)"          expect 400 'Sort column must be one of'
request -b "$WORK/viewer.jar" "$BASE/reports/monthly_revenue?from_month=%3Cscript%3Ealert(1)%3C/script%3E"
check "script in a parameter comes back escaped"              expect 400 '&lt;script&gt;alert(1)'
check "  ...and never as a raw <script> tag"                  body_lacks '<script>alert(1)'

request -b "$WORK/viewer.jar" "$BASE/api/reports/monthly_revenue?from_month=2017-01&to_month=2017-12"
check "JSON for monthly_revenue 2017 is 200"                  expect 200 '"report":"monthly_revenue"'
check "  ...with 12 rows"                                     test "$(grep -o '\["2017-' "$WORK/body" | wc -l)" -eq 12

# ------------------------------------------------------------------ scheduled export as analyst
login analyst "$ANALYST_PASSWORD" "$WORK/analyst.jar"
check "analyst logs in (302)"                                 expect 302
curl -s -b "$WORK/analyst.jar" -o "$WORK/exports.html" "$BASE/exports"
token="$(csrf_token "$WORK/exports.html")"

request -b "$WORK/analyst.jar" --data-urlencode "_csrf=$token" -d action=create -d report_code=monthly_revenue \
    --data-urlencode 'params=from_month=2017-01&to_month=2017-12&sort=month_start&dir=asc' -d run_time=05:30 "$BASE/exports"
schedule_id="$(grep -i '^Location:' "$WORK/headers" | grep -o 'created=[0-9]*' | cut -d= -f2)"
check "analyst creates a daily schedule (302, id ${schedule_id:-none})" expect 302 '' '^Location: /exports?created='

request -b "$WORK/analyst.jar" --data-urlencode "_csrf=$token" -d action=runNow -d "schedule_id=$schedule_id" "$BASE/exports"
check "analyst presses Run now (302)"                         expect 302 '' '^Location: /exports?queued'

echo "      waiting for the scheduler (it checks every 30 s)..."
run_id=""
for attempt in $(seq 1 30); do
    sleep 3
    curl -s -b "$WORK/analyst.jar" -o "$WORK/exports.html" "$BASE/exports"
    run_id="$(grep -o "data-run=\"[0-9]*\" data-schedule=\"$schedule_id\" data-status=\"SUCCESS\"" "$WORK/exports.html" \
              | head -1 | cut -d'"' -f2)"
    [ -n "$run_id" ] && break
done
check "scheduler ran the export: run ${run_id:-none} is SUCCESS" test -n "$run_id"

request -b "$WORK/analyst.jar" "$BASE/exports/download?run=$run_id"
cp "$WORK/body" "$WORK/export.csv"
check "owner downloads the CSV (200, text/csv)"               expect 200 '' '^Content-Type: text/csv'
check "  ...as an attachment"                                 grep -qi '^Content-Disposition: attachment' "$WORK/headers"
head -3 "$WORK/export.csv" | tr -d '\r' | sed 's/^/      /'
check "  ...with the report's header row"                     grep -q '^month_start,order_count,revenue,' "$WORK/export.csv"
check "  ...and the same 12 rows as the report"               test "$(($(wc -l < "$WORK/export.csv") - 1))" -eq 12

request -b "$WORK/viewer.jar" "$BASE/exports/download?run=$run_id"
check "viewer cannot download the analyst's export (404)"     expect 404
request -b "$WORK/analyst.jar" "$BASE/exports/download?run=../../etc/passwd"
check "path traversal in the run id is 400"                   expect 400

request -b "$WORK/analyst.jar" --data-urlencode "_csrf=$token" -d action=disable -d "schedule_id=$schedule_id" "$BASE/exports"
check "cleanup: the test schedule is disabled (302)"          expect 302 '' '^Location: /exports?disabled'

# ------------------------------------------------------------------ logout
request -b "$WORK/analyst.jar" "$BASE/logout"
check "logout by GET is not allowed (405)"                    expect 405
request -b "$WORK/analyst.jar" --data-urlencode "_csrf=$token" "$BASE/logout"
check "logout by POST with the token (302)"                   expect 302 '' '^Location: /login?loggedOut'
request -b "$WORK/analyst.jar" "$BASE/reports"
check "the old session no longer works (302 to /login)"       expect 302 '' '^Location: /login'

echo
echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
