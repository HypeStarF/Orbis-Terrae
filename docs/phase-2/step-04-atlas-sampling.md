# Phase 2 Step 4: atlas sampling

This step connects the data-driven Earth generator to the completed Phase 1 atlas runtime. It does not place
terrain blocks yet. Instead, it establishes one deterministic column-sampling contract that Step 5 can consume
without duplicating projection, atlas selection, interpolation, or vertical transformation logic.

## Bundled offline atlas

The reviewed `bergen-real-v1.zip` fixture is packaged inside the mod. On first common-side startup it is safely
extracted below the game directory:

```text
<game-directory>/orbis_terrae/atlases/bergen-real-v1/
```

The installer:

- rejects absolute and path-traversing ZIP entries;
- extracts into a temporary sibling directory;
- validates the result through `AtlasDirectory`;
- requires both elevation and land-mask layers;
- moves the validated directory into its final location;
- reuses a valid existing installation on later launches.

The bundle contains reviewed Copernicus-derived elevation and Natural Earth land-mask data around Bergen. It is
small enough to ship with the development mod and requires no network access or GIS libraries at runtime.

## Runtime ownership

`OrbisAtlasRuntime` owns the opened atlas directories and one `AtlasStack`. The stack retains the Phase 1
per-layer selection and fallback rules, including bounded tile caches and independent supplying atlas IDs.

`OrbisAtlasRuntimeManager` creates one common runtime from the NeoForge game directory. The mod entry point
initializes it on both integrated and dedicated servers, so the existing headless smoke now verifies that the
bundle is packaged, extracted, validated, and opened.

## Column sampling contract

`EarthAtlasSampler.sample(blockX, blockZ)` performs the complete read-only lookup for one Minecraft column:

1. Wrap Minecraft X with the selected world profile.
2. Convert wrapped X and Z to equirectangular longitude and latitude.
3. Bilinearly sample real elevation through `AtlasStack`.
4. Sample the nearest land-mask value through `AtlasStack`.
5. Transform available real elevation into the profile's Minecraft terrain Y.
6. Return the value and supplying atlas ID for each layer independently.

The result also records:

- the requested X and Z;
- the wrapped X used by the projection;
- whether Z lies inside the finite projected latitude range;
- the geographic coordinate used for atlas access;
- whether both terrain inputs are available.

Missing elevation does not erase an available land classification, and missing land-mask data does not erase an
available elevation. This preserves Phase 1's independent fallback behavior and lets Step 5 apply an explicit
missing-data policy.

## Chunk-generator integration

`OrbisChunkGenerator` lazily caches one `EarthAtlasSampler` for its immutable profile and exposes
`sampleAtlasColumn`. The debug information path reports geographic coordinates, transformed elevation, land or
water classification, and source atlas IDs.

`fillFromNoise`, `getBaseHeight`, and `getBaseColumn` still fail clearly. Step 5 will consume the new sampler to
fill deterministic stone, surface, seabed, air, and water columns.

## Verification

Focused Step 4 checks:

```bash
./gradlew phase2AtlasSamplingCheck --no-configuration-cache --warning-mode=fail
```

The tests cover:

- exact sampling at the committed multi-tile fixture origin;
- profile vertical transformation from metres to terrain Y;
- longitude wrapping across one complete projected Earth width;
- independent elevation no-data and land-mask results;
- projected-latitude boundary reporting;
- bundled Bergen ZIP extraction and reuse;
- real Bergen elevation and land-mask access through Minecraft coordinates.

Complete Phase 2 verification remains:

```bash
./gradlew phase2Check --no-configuration-cache --warning-mode=fail
```

## Deferred to Step 5

The bundled fixture covers Bergen rather than the complete Earth. Outside installed atlas coverage, Step 4 returns
missing samples instead of inventing data. Step 5 must define the initial missing-data behavior and perform actual
terrain-column filling. Broader global and Northern Europe atlas installation remains a later integration upgrade.
