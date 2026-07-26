# Phase 0 acceptance checklist

Complete this checklist on the primary development machine and on the intended dedicated server.
Record results in `docs/phase-0/verification-record.md`.

## Automated gate

- [ ] `java -version` reports Java 21.
- [ ] `./gradlew wrapper --gradle-version 9.2.1` generates the canonical wrapper JAR and scripts.
- [ ] The canonical wrapper changes are committed.
- [ ] `./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail` succeeds from a clean checkout.
- [ ] A second `./gradlew phase0Check --no-configuration-cache --warning-mode=fail` succeeds without cleaning.
- [ ] CI succeeds on the default branch.
- [ ] `git status --short` is empty after the checks.

## Client smoke test

- [ ] Run `./gradlew :modules:minecraft-mod:runClient --no-configuration-cache`.
- [ ] Minecraft reaches the title screen.
- [ ] The Mods screen lists `Orbis Terrae` with mod ID `orbis_terrae`.
- [ ] The log contains `Orbis Terrae common entry point loaded`.
- [ ] The log contains `Orbis Terrae client setup complete`.
- [ ] Exit normally and confirm no crash report was created.

## Dedicated-server smoke test

- [ ] Run `./gradlew :modules:minecraft-mod:runServer --no-configuration-cache` once.
- [ ] Accept the Minecraft EULA only after reading it, then rerun if required.
- [ ] The server reaches the normal ready state.
- [ ] The log contains `Orbis Terrae common entry point loaded`.
- [ ] The log does not contain `net.minecraft.client`, `DistExecutor`, or classloading errors.
- [ ] Stop the server cleanly with `stop`.
- [ ] Launch the server again and confirm the world reopens.

## Identity and architecture

- [ ] Main class is `me.sdmannen.orbis_terrae.OrbisTerrae`.
- [ ] Artifact name begins with `orbis-terrae`.
- [ ] `atlas-api` has no Minecraft or NeoForge dependency.
- [ ] `atlas-compiler` has no Minecraft or NeoForge dependency.
- [ ] Client-only imports exist only under `me.sdmannen.orbis_terrae.client`.
- [ ] ADR-0001, ADR-0002, and ADR-0003 are reviewed.
- [ ] No dataset is marked approved without evidence.

Phase 0 is complete only when every applicable box is checked and the verification record is committed.
