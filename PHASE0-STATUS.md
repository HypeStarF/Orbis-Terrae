# Phase 0 status

## Implemented

- Java 21 and NeoForge ModDevGradle project configuration.
- Multi-module repository and dependency rules.
- Empty common mod entry point plus physically client-only entry point.
- Client, dedicated-server, GameTest server, and data run configurations.
- JUnit, Checkstyle, compiler linting, formatting verification, and GitHub Actions CI.
- ADRs, development setup, Phase 0 acceptance checklist, and verification record.
- Dataset licensing matrix and provenance process.
- Draft profile, manifest, atlas-manifest, and provenance schemas.
- Initial pure-Java atlas and compatibility API placeholders.
- Gradle 9.2.1 deprecation warnings are treated as build failures.
- Gradle configuration caching is disabled for Phase 0 verification.

## Verified in the generation environment

- Java 21 is installed.
- Repository text formatting and JSON schema syntax pass local checks.
- Minecraft-independent production Java sources compile with `javac --release 21`.
- Git repository initializes and commits cleanly.

## Still requires the developer machine

- Resolve Gradle, NeoForge, JUnit, and Checkstyle dependencies from the internet.
- Generate and commit the canonical Gradle wrapper.
- Run `./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail`.
- Run the Minecraft client smoke test.
- Run and restart the dedicated server smoke test.
- Commit the completed `docs/phase-0/verification-record.md`.

Phase 0 is not accepted until those smoke tests are recorded.
