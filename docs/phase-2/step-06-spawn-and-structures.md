# Phase 2 Step 6: safe geographic spawn and structure policy

This step removes vanilla artificial structure generation from the Orbis Terrae dimension and replaces vanilla
spawn selection with one deterministic geographic target and bounded safety search. It does not add climate,
freshwater, vegetation, biome, or hazard scoring.

## Artificial structures

`OrbisChunkGenerator` suppresses artificial structures at the generator boundary:

- `createStructures` creates no structure starts;
- `createReferences` creates no cross-chunk structure references;
- `findNearestMapStructure` returns no result.

This policy applies to every vanilla or data-driven structure set presented to the Orbis Terrae generator, including
villages, mineshafts, strongholds, trial chambers, ancient cities, monuments, shipwrecks, ruined portals, temples,
outposts, ocean ruins, igloos, and woodland mansions. Nether and End dimensions in the Earth preset retain their
vanilla generators and are not affected.

The policy prevents new artificial structures. It does not delete structure starts already stored in chunks created
by an older generator version.

## Serialized geographic spawn

The Earth chunk-generator codec now contains a `spawn` object:

```json
{
  "mode": "coordinates",
  "latitude": 60.3913,
  "longitude": 5.3221,
  "search_radius_blocks": 64
}
```

Step 6 supports exact decimal-degree coordinates. Latitude and longitude are validated before world creation, and
the search radius is bounded to 256 blocks. The bundled preset targets Bergen because the reviewed development
atlas covers that area.

The spawn object is serialized with the level's chunk-generator configuration, so save and reload retain the same
requested target. Step 7 will validate that persistence together with the standalone immutable world manifest.
Named points, region targets, and random-region selection remain later configuration modes.

## Deterministic safety search

`GeographicSpawnResolver` converts the requested coordinate through the selected immutable world profile and then
checks candidates in a fixed order:

1. Check the exact projected block.
2. Search square rings around it from the configured radius of one block outward.
3. Visit every ring clockwise from its north-west corner.
4. Return the first candidate satisfying every current safety rule.

A candidate is accepted only when:

- the center terrain column is land;
- all eight neighboring columns are land, avoiding immediate water edges;
- each neighboring solid surface differs by at most eight blocks;
- the player position is one block above the solid surface;
- at least two blocks of vertical build-space clearance remain.

Terrain plans are cached for the duration of the one-time search. If the configured target has no safe candidate,
the resolver retries the bundled Bergen target. World initialization fails clearly if neither bounded search can
find safe land; it never silently places the player in the fallback ocean.

## World-creation lifecycle

`OrbisSpawnEvents` handles NeoForge's first-spawn creation event on the common event bus. It acts only when the
server level uses `OrbisChunkGenerator`, writes the resolved default spawn position, and then cancels vanilla spawn
selection. Other dimensions and non-Orbis worlds remain unchanged.

## Verification

Focused Step 6 checks:

```bash
./gradlew phase2SpawnStructureCheck --no-configuration-cache --warning-mode=fail
```

The tests cover:

- exact safe-coordinate acceptance;
- deterministic square-ring ordering;
- rejection of water edges and excessive local slope;
- real bundled Bergen atlas resolution;
- explicit generator-level structure suppression;
- spawn codec and preset fields;
- common-side event isolation and vanilla-spawn cancellation.

Complete Phase 2 verification remains:

```bash
./gradlew phase2Check --no-configuration-cache --warning-mode=fail
```

## Deferred to Step 7 and later phases

Step 7 adds chunk determinism, save/reload, client, and dedicated-server validation, including persistence of the
spawn target and immutable manifest agreement. Later systems may expand spawn scoring with climate, freshwater,
vegetation, hazards, biome restrictions, and regional selection after those atlas layers exist.
