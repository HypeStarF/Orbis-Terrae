# Phase 0 verification record

- Verification date: TBD
- Commit: TBD
- Developer machine: TBD
- Operating system: TBD
- Java distribution/version: TBD
- Dedicated server machine: Intel i7-8700 target; actual result TBD

## Commands and results

| Command/test | Result | Evidence or log path |
| --- | --- | --- |
| `./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail` | NOT RUN | |
| CI | NOT RUN | |
| `runClient` smoke test | NOT RUN | |
| `runServer` first launch | NOT RUN | |
| `runServer` restart | NOT RUN | |

## Deviations

- NeoForge is temporarily pinned to `21.1.243`; see ADR-0002.
- Gradle configuration caching is disabled for Phase 0 verification.

## Sign-off

- Phase 0 accepted: NO
- Accepted by: TBD
