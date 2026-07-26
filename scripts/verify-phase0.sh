#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

java -version
./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail --stacktrace

git diff --check
if [ -n "$(git status --short)" ]; then
    echo "Repository changed during verification:" >&2
    git status --short >&2
    exit 1
fi

echo "Automated Phase 0 checks passed. Complete the client/server smoke-test checklist next."
