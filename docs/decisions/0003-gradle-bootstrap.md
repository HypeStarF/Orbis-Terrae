# ADR-0003: Bootstrap Gradle, then generate the canonical wrapper

- Status: Accepted, temporary
- Date: 2026-07-26

## Context

A normal Gradle repository commits `gradlew`, `gradlew.bat`, wrapper properties, and the binary
`gradle-wrapper.jar`. The generated project package cannot safely acquire that opaque binary inside
its restricted build environment, although the official NeoForge template identifies Gradle 9.2.1.

## Decision

Ship small, reviewable bootstrap launchers that download and run Gradle 9.2.1. On the first normal
development machine, run:

```bash
./gradlew wrapper --gradle-version 9.2.1
```

Commit the resulting official wrapper scripts and `gradle/wrapper/gradle-wrapper.jar`, replacing the
temporary bootstrap launchers.

## Consequences

- The initial package remains executable from a clean checkout with Java 21 and internet access.
- The temporary launcher is readable source rather than an embedded binary.
- Phase 0 acceptance requires generating and committing the canonical Gradle wrapper.
