# Orbis Terrae

Orbis Terrae is a NeoForge 1.21.1 project for deterministic, offline Earth world generation.
Phase 0 established the build and server-safe mod foundation. Phase 1 completed the atlas proof of concept.
Phase 2 now connects that atlas foundation to the first playable Earth terrain.

## Project identity

| Field | Value |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge requested | `21.1.244` |
| NeoForge temporary build pin | `21.1.243` |
| Java | `21` |
| Gradle | `9.6.1` |
| Development version | `0.2.0-SNAPSHOT` |
| Current phase | Phase 2 — Basic Earth dimension |
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
./gradlew clean allChecks --no-configuration-cache --warning-mode=fail
./gradlew :modules:minecraft-mod:runClient --no-configuration-cache
./gradlew :modules:minecraft-mod:runServer --no-configuration-cache
./gradlew :modules:atlas-compiler:run --args="--version" --no-configuration-cache
./gradlew :modules:atlas-compiler:run \
  --args="validate-manifest atlas/test-fixtures/manifest-v1/atlas-manifest.json" \
  --no-configuration-cache
```

Windows:

```bat
gradlew.bat clean allChecks --no-configuration-cache --warning-mode=fail
gradlew.bat :modules:minecraft-mod:runClient --no-configuration-cache
gradlew.bat :modules:minecraft-mod:runServer --no-configuration-cache
gradlew.bat :modules:atlas-compiler:run --args="--version" --no-configuration-cache
gradlew.bat :modules:atlas-compiler:run ^
  --args="validate-manifest atlas/test-fixtures/manifest-v1/atlas-manifest.json" ^
  --no-configuration-cache
```

Configuration caching is disabled for verification. Build caching remains enabled.
The committed wrapper downloads Gradle 9.6.1 and verifies the distribution against its published
SHA-256 checksum.

## Verification scopes

| Task | Scope |
| --- | --- |
| `phase0Check` | Foundation regressions, module placeholders, formatting, and server-safety boundaries |
| `phase1Check` | Atlas API and atlas compiler checks |
| `phase2FoundationCheck` | Phase 2 profile, projection, vertical-curve, and manifest contracts |
| `phase2CodecCheck` | Phase 2 biome-source and chunk-generator codec contracts |
| `phase2Check` | Every Phase 2 check implemented so far |
| `allChecks` | Every automated check across every module and phase |
| `check` | Gradle's canonical full verification lifecycle task; also runs every subproject check |

Use a phase task for focused development feedback. Use `allChecks` before pushing or merging. Gradle reuses
up-to-date outputs and the build cache, so running a focused task followed by `allChecks` does not require
all unchanged work to execute again.

## Project status

Phase 0 was accepted on 2026-07-26 after the clean build, strict CI checks, client smoke test,
dedicated-server smoke test, restart test, identity review, and architecture checks were signed off.
See [`PHASE0-STATUS.md`](PHASE0-STATUS.md) and
[`docs/phase-0/verification-record.md`](docs/phase-0/verification-record.md).

Phase 1 closed on 2026-07-27 after the atlas manifest, deterministic tile format, geographic samplers,
regional and global atlas builds, runtime selection, corruption fallback, cache bounds, and performance
exit audit were completed. See [`docs/phase-1/README.md`](docs/phase-1/README.md).

Phase 2 now has immutable world profiles, centred equirectangular coordinate mapping, nonlinear vertical
curves, world manifest schema v1, and registered `orbis_terrae:earth` biome-source and chunk-generator
codecs. The next step adds dimension data and minimal biome selection. See
[`docs/phase-2/README.md`](docs/phase-2/README.md).

## Modules

- `minecraft-mod`: NeoForge entry point, Earth world profiles, manifests, worldgen codecs, and run configurations.
- `atlas-api`: Minecraft-independent tile format, strict atlas manifest, coordinate sampling, and cache.
- `atlas-compiler`: command-line packer and manifest validator/canonicalizer.
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
- World-defining settings must be copied into the immutable world manifest.
