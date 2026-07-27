# Phase 2 Step 7: determinism, persistence, and runtime validation

Step 7 validates the Phase 2 terrain pipeline without adding new geographic features. It establishes stable
chunk-content fingerprints, strengthens the immutable world manifest, verifies NeoForge launch preparation, and
records the client and dedicated-server save/reopen procedure required before the Phase 2 exit audit.

## Deterministic chunk fingerprints

`TerrainChunkFingerprint` hashes one chunk's complete 16 by 16 set of `TerrainColumnPlan` values in fixed
coordinate order. The fingerprint includes:

- chunk and absolute block coordinates;
- land or ocean classification;
- solid-top, sea-level, and build-height values;
- atlas data-availability classification.

The fingerprint represents every input used by the current solid-column material policy. A changed column,
coordinate, profile height, or data-availability result changes the SHA-256 output.

The focused test resolves the Bergen chunk from two separately installed copies of the bundled atlas and requires
identical fingerprints. It also repeats the same computation in one runtime and verifies that a synthetic one-block
height change produces a different fingerprint.

## Immutable manifest persistence

The world manifest now copies exact spawn settings alongside the profile snapshot. Its configuration hash includes:

- schema version;
- generator and atlas versions;
- projection;
- world seed;
- all profile and vertical-curve values;
- spawn mode, coordinate, and search radius.

Manifest construction and JSON loading recompute the hash. A well-formed but modified seed, spawn, profile, atlas,
or generator setting is rejected rather than accepted under an old hash.

The persistence test writes `world/orbis_terrae/manifest.json`, reopens it from disk, and requires exact record and
spawn-configuration equality. Unknown fields, trailing data, missing required values, and hash mismatches remain
strict failures.

## NeoForge runtime preparation

`phase2RuntimePreparationCheck` runs ModDevGradle's client and server preparation tasks and verifies these generated
files are present and non-empty:

- `modules/minecraft-mod/build/moddev/clientRunVmArgs.txt`
- `modules/minecraft-mod/build/moddev/serverRunVmArgs.txt`

This check runs through Linux `allChecks` and separately on Windows CI. A `clean` build can therefore no longer leave
a stale IntelliJ configuration pointing at an ungenerated client VM-argument file without CI detecting it.

The existing `phase2WorldgenSmoke` continues to launch the NeoForge GameTest server headlessly. It validates common
entry-point loading, dedicated-server-safe class boundaries, bundled-atlas installation, worldgen registry decoding,
and spawn-event registration.

## Focused commands

Linux or macOS:

```bash
./gradlew phase2DeterminismPersistenceCheck \
  phase2RuntimePreparationCheck \
  --no-configuration-cache \
  --warning-mode=fail
```

Windows PowerShell:

```powershell
.\gradlew.bat phase2DeterminismPersistenceCheck `
  phase2RuntimePreparationCheck `
  --no-configuration-cache `
  --warning-mode=fail
```

## Manual client acceptance

Use a newly created Orbis Terrae world for this check.

1. Run `:modules:minecraft-mod:runClient`.
2. Create an Orbis Terrae world with a fixed seed and the bundled preset.
3. Confirm the player spawns on solid land near the configured Bergen target.
4. Record the spawn coordinates and inspect nearby coastline and terrain.
5. Save and return to the title screen.
6. Reopen the same world and confirm the spawn, terrain, and loaded chunks are unchanged.
7. Close the client normally and confirm no save or shutdown error appears in `latest.log`.

## Manual dedicated-server acceptance

1. Prepare the server run and accept the development EULA.
2. Configure the server to create an `orbis_terrae:earth` world with a fixed seed.
3. Start the server and wait for the normal ready message.
4. Join once or use the console to force-load terrain near spawn.
5. Stop the server through the `stop` command.
6. Start the same save again without deleting any world or atlas files.
7. Confirm the existing world opens, the spawn remains unchanged, and no manifest, codec, atlas, or chunk error is
   logged.

The graphical and full dedicated-server restart checks are intentionally local acceptance tests. Headless CI does
not simulate a display-backed singleplayer session or keep an interactive production-style server running.

## Scope boundary

Step 7 does not add hydrology, caves, biomes, vegetation, structures, named-place spawn modes, or migration support.
Long-running multiplayer soak tests and terrain performance benchmarks remain Step 8 and later phase work.
