#!/usr/bin/env bash
# Print the BCrypt hash of a password, for creating users or resetting a password:
#
#   bin/hash-password.sh                       (asks for the password, not echoed)
#   UPDATE users SET password_hash = '<hash>' WHERE username = 'admin';
#
# The password travels on standard input, never as a command-line argument,
# so it does not end up in shell history or the process list.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ -t 0 ]; then
    read -r -s -p "Password: " password
    echo >&2
else
    read -r password
fi

printf '%s\n' "$password" | "$ROOT/bin/mvn.sh" -q compile exec:java \
    -Dexec.mainClass=com.reportingportal.auth.HashPassword
