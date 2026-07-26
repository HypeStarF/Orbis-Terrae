# Phase 0 verification record

- Verification date: 2026-07-26
- Acceptance checklist commit: `04bcbfeb13916218f83a4cc8ec721387bebcc645`
- Acceptance authority: HypeStarF (project owner)
- Developer machine: Project owner's development machine; hardware details were not recorded
- Operating system: Not recorded
- Java distribution/version: Java 21; distribution and exact build were not recorded
- Dedicated server machine: Intel i7-8700 target; smoke-test completion confirmed by the project owner

## Commands and results

| Command/test | Result | Evidence or log path |
| --- | --- | --- |
| `./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail` | PASS | Confirmed by the project owner in acceptance commit `04bcbfeb13916218f83a4cc8ec721387bebcc645` |
| Second `./gradlew phase0Check --no-configuration-cache --warning-mode=fail` without cleaning | PASS | Confirmed by the project owner in the acceptance checklist |
| CI | PASS | Gradle 9.6.1 strict CI passed during the wrapper upgrade; default-branch CI was confirmed in the acceptance checklist |
| `runClient` smoke test | PASS | Client reached the title screen, listed Orbis Terrae, logged common and client setup, and exited without a crash report; confirmed by the project owner |
| `runServer` first launch | PASS | Dedicated server reached its ready state without client-classloading errors; confirmed by the project owner |
| `runServer` restart | PASS | Server stopped cleanly and reopened the world; confirmed by the project owner |

## Architecture and identity verification

- Main class: `me.sdmannen.orbis_terrae.OrbisTerrae`
- Mod ID: `orbis_terrae`
- Artifact prefix: `orbis-terrae`
- `atlas-api` and `atlas-compiler` remain independent of Minecraft and NeoForge runtime classes.
- Client-only imports remain under `me.sdmannen.orbis_terrae.client`.
- ADR-0001, ADR-0002, and ADR-0003 were reviewed.
- No dataset is approved without recorded licensing and provenance evidence.

## Deviations and evidence limitations

- NeoForge is temporarily pinned to `21.1.243`; see ADR-0002.
- Gradle configuration caching is disabled for Phase 0 verification.
- Raw local client/server logs, screenshots, exact OS details, and exact JDK distribution details were not committed. Local smoke-test results are accepted on the project owner's explicit sign-off in commit `04bcbfeb13916218f83a4cc8ec721387bebcc645`.

## Sign-off

- Phase 0 accepted: YES
- Accepted by: HypeStarF
- Accepted on: 2026-07-26
- Acceptance basis: Completed checklist, successful strict Gradle/CI validation, and confirmed client and dedicated-server smoke tests
