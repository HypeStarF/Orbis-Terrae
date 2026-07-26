# Phase 0 acceptance checklist

**Status:** Complete

**Accepted on:** 2026-07-26

**Accepted by:** HypeStarF

**Checklist completion commit:** `04bcbfeb13916218f83a4cc8ec721387bebcc645`

This checklist was completed on the primary development machine and the intended dedicated server.
Detailed results and evidence limitations are recorded in `docs/phase-0/verification-record.md`.

## Automated gate

- [x] `java -version` reports Java 21.
- [x] `./gradlew wrapper --gradle-version 9.6.1 --gradle-distribution-sha256-sum 9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14` reproduces the canonical wrapper files.
- [x] The wrapper JAR SHA-256 is `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.
- [x] The canonical wrapper changes are committed.
- [x] `./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail` succeeds from a clean checkout.
- [x] A second `./gradlew phase0Check --no-configuration-cache --warning-mode=fail` succeeds without cleaning.
- [x] CI succeeds on the default branch.
- [x] `git status --short` is empty after the checks.

## Client smoke test

- [x] Run `./gradlew :modules:minecraft-mod:runClient --no-configuration-cache`.
- [x] Minecraft reaches the title screen.
- [x] The Mods screen lists `Orbis Terrae` with mod ID `orbis_terrae`.
- [x] The log contains `Orbis Terrae common entry point loaded`.
- [x] The log contains `Orbis Terrae client setup complete`.
- [x] Exit normally and confirm no crash report was created.

## Dedicated-server smoke test

- [x] Run `./gradlew :modules:minecraft-mod:runServer --no-configuration-cache` once.
- [x] Accept the Minecraft EULA only after reading it, then rerun if required.
- [x] The server reaches the normal ready state.
- [x] The log contains `Orbis Terrae common entry point loaded`.
- [x] The log does not contain `net.minecraft.client`, `DistExecutor`, or classloading errors.
- [x] Stop the server cleanly with `stop`.
- [x] Launch the server again and confirm the world reopens.

## Identity and architecture

- [x] Main class is `me.sdmannen.orbis_terrae.OrbisTerrae`.
- [x] Artifact name begins with `orbis-terrae`.
- [x] `atlas-api` has no Minecraft or NeoForge dependency.
- [x] `atlas-compiler` has no Minecraft or NeoForge dependency.
- [x] Client-only imports exist only under `me.sdmannen.orbis_terrae.client`.
- [x] ADR-0001, ADR-0002, and ADR-0003 are reviewed.
- [x] No dataset is marked approved without evidence.

All Phase 0 exit criteria are accepted. Development proceeds on Phase 1 — Atlas proof of concept.
