# Phase 1 multi-tile reference atlas

The permanent fixture at `atlas/test-fixtures/multi-tile-v1` is the synthetic reference atlas for
Phase 1 Step 4. It is deliberately small enough to inspect manually while using the same manifest,
OTAT reader, corruption checks, path resolution, cache and geographic samplers as a future real atlas.

The fixture is project-owned synthetic test data. It is not derived from a real GIS dataset and does
not establish any dataset licence or redistribution decision.

## Geometry

- projection: equirectangular
- bounds: west `0`, south `0`, east `3`, north `3`
- raster dimensions: `4 x 4` samples
- tile dimensions: `2 x 2` samples
- tile grid: `2 x 2` tiles per layer
- zoom: `0`

Raster Y increases southward. The top row therefore has latitude `3`, and the bottom row has latitude
`0`.

## Elevation samples

Values are signed integer metres. `ND` is the OTAT elevation no-data value `-32768`.

```text
       longitude
       0    1    2    3
lat 3   0   10   20   30
lat 2 100  110  120  130
lat 1 200  210  220  230
lat 0 300  310  320   ND
```

The point at latitude `1.5`, longitude `1.5` lies where all four elevation tiles meet. Bilinear
interpolation must produce exactly `165.0` metres from samples `110`, `120`, `210` and `220`.

## Land-mask samples

`L` means land and `W` means water.

```text
       longitude
       0  1  2  3
lat 3  L  L  W  W
lat 2  L  W  W  W
lat 1  L  L  L  W
lat 0  W  W  L  L
```

Land masks are sampled with nearest-neighbour classification.

## File layout

```text
multi-tile-v1/
├── atlas-manifest.json
└── layers/
    ├── elevation/0/{x}/{y}.otat
    └── land-mask/0/{x}/{y}.otat
```

There are four binary tiles for each layer. The checked-in directory can be opened directly by
`AtlasDirectory`.

## Regeneration

Generate a fresh copy in another directory:

```bash
./gradlew :modules:atlas-compiler:run \
  --args="generate-synthetic-fixture build/generated/multi-tile-v1" \
  --no-configuration-cache
```

Verify a generated directory byte-for-byte:

```bash
./gradlew :modules:atlas-compiler:run \
  --args="verify-synthetic-fixture build/generated/multi-tile-v1" \
  --no-configuration-cache
```

The repository test compares every checked-in manifest and OTAT byte against fresh output from
`SyntheticAtlasFixture`. Changing the fixture therefore requires an intentional generator change and a
matching update to the committed files.

## Test purposes

The fixture is the reusable reference for:

- exact sample lookup
- interpolation inside one tile
- interpolation across horizontal and vertical boundaries
- interpolation where four tiles meet
- atlas edge behavior
- no-data propagation
- land/water classification
- cache reuse and eviction
- missing, corrupt and incompatible tile behavior
- deterministic compiler output

Later steps may add larger generated fixtures, but this one remains the minimal contract test for the
complete atlas runtime path.
