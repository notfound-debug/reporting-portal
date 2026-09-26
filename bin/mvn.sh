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

# The repo folder, as Docker must see it. In Git Bash, `pwd -W` gives the Windows form
# (C:/Users/...): plain `pwd` can return an MSYS-only path such as /tmp/..., which
# Docker would look up inside its Linux VM and mount an empty folder. Elsewhere,
# `pwd -W` does not exist and plain `pwd` is used.
ROOT="$(cd "$(dirname "$0")/.." && (pwd -W 2>/dev/null || pwd))"

# -i passes standard input through (bin/hash-password.sh pipes a password in).
docker run --rm -i \
    -v "$ROOT":/build \
    -v reporting-portal-m2:/root/.m2 \
    -w /build \
    maven:3.9-eclipse-temurin-17 \
    mvn -B "$@"
