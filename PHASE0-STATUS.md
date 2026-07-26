# Phase 0 status

**Status:** Accepted  
**Accepted on:** 2026-07-26  
**Acceptance checklist commit:** `04bcbfeb13916218f83a4cc8ec721387bebcc645`  
**Accepted by:** HypeStarF

## Completed foundation

- Java 21 and NeoForge ModDevGradle project configuration.
- Canonical Gradle 9.6.1 wrapper with distribution and wrapper checksum verification.
- Multi-module repository and dependency rules.
- Empty common mod entry point plus physically client-only entry point.
- Client, dedicated-server, GameTest server, and data run configurations.
- JUnit, Checkstyle, compiler linting, formatting verification, and GitHub Actions CI.
- Gradle deprecation warnings treated as build failures.
- ADRs, development setup, acceptance checklist, and verification record.
- Dataset licensing matrix and provenance process.
- Draft profile, world-manifest, atlas-manifest, and provenance schemas.
- Initial pure-Java atlas and compatibility API placeholders.

## Exit criteria

- [x] Empty Orbis Terrae mod loads in the client.
- [x] Empty Orbis Terrae mod loads on a dedicated server.
- [x] Build succeeds from a clean checkout.
- [x] Automated tests and static checks run successfully in CI.
- [x] Project identifiers are correct.
- [x] Client-only code does not load on the dedicated server.
- [x] Dedicated server stops cleanly and reopens its world.

The complete evidence and evidence limitations are recorded in
[`docs/phase-0/verification-record.md`](docs/phase-0/verification-record.md).

## Accepted deviations

- NeoForge remains temporarily pinned to `21.1.243`; see ADR-0002.
- Gradle configuration caching remains disabled for Phase 0 verification.
- Local client/server smoke-test details are based on the project owner's explicit sign-off; raw local logs and exact machine metadata were not committed.

## Next phase

Phase 1 — Atlas proof of concept is now active. Its first objectives are the atlas manifest and tile-format prototypes, deterministic compiler CLI foundations, coordinate conversion, elevation and land-mask imports, atlas reading, and tile caching.
