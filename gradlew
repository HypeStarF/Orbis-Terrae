#!/bin/sh
set -eu

GRADLE_VERSION="9.2.1"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_ROOT="$GRADLE_USER_HOME/orbis-terrae-bootstrap"
GRADLE_HOME="$INSTALL_ROOT/gradle-$GRADLE_VERSION"
ARCHIVE="$INSTALL_ROOT/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$INSTALL_ROOT"
    if [ ! -f "$ARCHIVE" ]; then
        echo "Downloading Gradle $GRADLE_VERSION..." >&2
        if command -v curl >/dev/null 2>&1; then
            curl --fail --location --retry 3 --output "$ARCHIVE" "$URL"
        elif command -v wget >/dev/null 2>&1; then
            wget --output-document="$ARCHIVE" "$URL"
        else
            echo "Neither curl nor wget is available. Install one, or install Gradle $GRADLE_VERSION manually." >&2
            exit 1
        fi
    fi
    rm -rf "$GRADLE_HOME"
    (cd "$INSTALL_ROOT" && jar xf "$ARCHIVE")
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
