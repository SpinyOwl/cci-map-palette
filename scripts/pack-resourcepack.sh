#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

if [ -x "./gradlew" ]; then
  exec ./gradlew packResourcepack "$@"
fi

exec gradle packResourcepack "$@"