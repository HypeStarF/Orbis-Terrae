# Orbis Terrae

Orbis Terrae is a NeoForge 1.21.1 project for deterministic, offline Earth world generation.
Phase 0 established the build, module, CI, architecture, provenance, and dedicated-server-safe mod
foundation. Phase 1 now focuses on the atlas proof of concept.

## Project identity

| Field | Value |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge requested | `21.1.244` |
| NeoForge temporary build pin | `21.1.243` |
| Java | `21` |
| Gradle | `9.6.1` |
| Development version | `0.1.0-SNAPSHOT` |
| Current phase | Phase 1 — Atlas proof of concept |
| Mod ID | `orbis_terrae` |
| Group | `me.sdmannen` |
| Artifact | `orbis-terrae` |
| Main class | `me.sdmannen.orbis_terrae.OrbisTerrae` |

`21.1.244` was not published in the official NeoForge Maven index when this scaffold was created
on 2026-07-26. ADR-0002 records the temporary pin to `21.1.243`.

## First commands

Linux/macOS:

```bash
chmod +x gradlew
./gradlew clean phase0Check --no-configuration-cache --warning-mode=fail
./gradlew :modules:minecraft-mod:runClient --no-configuration-cache
./gradlew :modules:minecraft-mod:runServer --no-configuration-cache
```

Windows:

```bat
gradlew.bat clean phase0Check --no-configuration-cache --warning-mode=fail
gradlew.bat :modules:minecraft-mod:runClient --no-configuration-cache
gradlew.bat :modules:minecraft-mod:runServer --no-configuration-cache
```

Configuration caching is disabled for Phase 0 verification. Build caching remains enabled.
The committed wrapper downloads Gradle 9.6.1 and verifies the distribution against its published
SHA-256 checksum.

## Project status

Phase 0 was accepted on 2026-07-26 after the clean build, strict CI checks, client smoke test,
dedicated-server smoke test, restart test, identity review, and architecture checks were signed off.
See [`PHASE0-STATUS.md`](PHASE0-STATUS.md) and
[`docs/phase-0/verification-record.md`](docs/phase-0/verification-record.md).

Phase 1 is the atlas proof of concept. It will establish the atlas manifest, tile format, compiler
CLI, coordinate conversion, deterministic elevation and land-mask imports, atlas reader, and tile
cache before Minecraft world generation work begins.

## Modules

- `minecraft-mod`: NeoForge entry point and run configurations.
- `atlas-api`: Minecraft-independent atlas contracts and coordinate value objects.
- `atlas-compiler`: command-line preprocessing application placeholder.
- `compatibility-api`: stable compatibility contracts.
- `compatibility-mekanism`: optional integration placeholder.
- `compatibility-immersive-engineering`: optional integration placeholder.
- `test-support`: shared deterministic fixtures and test helpers.

## Rules

- Java 21 only.
- No Minecraft classes in `atlas-api` or `atlas-compiler`.
- No `net.minecraft.client` references outside the `client` package.
- No raw GIS processing in the runtime mod.
- No dataset is approved for redistribution until the licensing matrix says `APPROVED`.
