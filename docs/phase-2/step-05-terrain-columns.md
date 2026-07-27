# Phase 2 Step 5: deterministic terrain columns

This step turns the read-only Step 4 atlas samples into the first generated Earth blocks. It deliberately remains a
column generator: caves, procedural micro-detail, biome surfaces, structures, hydrology, vegetation, and resource
placement are outside this boundary.

## Column policy

`TerrainColumnPlan` converts one `EarthAtlasSampler.ColumnSample` into a complete vertical material plan.

- Complete land data uses transformed elevation, clamped to at least sea level.
- Complete ocean data uses transformed elevation, clamped below sea level so water remains present.
- A land mask without elevation becomes flat land at sea level.
- Elevation without a land mask is treated conservatively as ocean.
- Missing elevation and land-mask data becomes a deterministic ocean with a floor 16 blocks below sea level.

The fallback ocean is intentionally obvious and temporary. It keeps generation total outside the bundled Bergen
fixture without claiming that missing atlas coverage is real geography.

## Initial materials

Each generated column contains only the minimum materials needed for the terrain prototype:

- stone below the surface layers;
- grass over three dirt blocks on land;
- four sand blocks at the seabed;
- source water from the seabed to sea level;
- air above the highest terrain or water block.

The generator fills these blocks in `fillFromNoise`, primes the world-surface and ocean-floor generation heightmaps,
and uses the same plan for `getBaseHeight` and `getBaseColumn`.

## Deferred systems

`applyCarvers`, `buildSurface`, and original mob spawning are no-ops in this step. Surface materials are already
placed by the column fill, while caves, biome-specific surfaces, natural population, structures, and geographic
spawn are implemented in later steps.

## Verification

The terrain policy tests cover:

- land clamping and soil layers;
- ocean clamping, seabed, and water;
- land-mask-only behavior;
- no-data fallback ocean behavior;
- height values used by world-surface and ocean-floor queries.

Repository-wide verification continues to run the production JAR inspection and headless NeoForge startup so the
new chunk generator is compiled, packaged, loaded, and decoded on the common server path.
