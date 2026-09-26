#!/usr/bin/env bash
# Run Maven inside a container, so no JDK or Maven has to be installed on this PC.
#
#   bin/mvn.sh test          run the unit tests
#   bin/mvn.sh package       build target/reporting-portal.war
#
# The repo is mounted at /build. Downloaded dependencies are kept in the Docker
# volume "reporting-portal-m2", so they are only downloaded once.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # stop Git Bash on Windows rewriting /paths inside docker arguments

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# -i passes standard input through (bin/hash-password.sh pipes a password in).
docker run --rm -i \
    -v "$ROOT":/build \
    -v reporting-portal-m2:/root/.m2 \
    -w /build \
    maven:3.9-eclipse-temurin-17 \
    mvn -B "$@"
