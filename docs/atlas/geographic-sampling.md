# Geographic atlas sampling

Phase 1 Step 3 converts latitude and longitude into values stored in compiled OTAT tiles. It builds on
the strict manifest contract and the atlas-directory runtime; it does not perform Minecraft coordinate
conversion or terrain generation.

## Coordinate conversion

Each layer declares its total raster width and height. For an equirectangular atlas, longitude maps to
the horizontal sample coordinate and latitude maps to the vertical sample coordinate:

```text
sampleX = (longitude - west) / (east - west) * (gridWidthSamples - 1)
sampleY = (north - latitude) / (north - south) * (gridHeightSamples - 1)
```

Raster Y increases downward, so northern latitudes have smaller sample-Y values. Both manifest bounds
are inclusive. Coordinates outside the atlas bounds are rejected before any tile is loaded.

## Global samples and tile addresses

The sampler works with global raster coordinates first. A global integer sample is mapped to its tile
and local position using:

```text
tileX = sampleX / tileSize
localX = sampleX % tileSize
tileY = sampleY / tileSize
localY = sampleY % tileSize
```

Every read goes through `AtlasLayer`, so Step 2 path validation, OTAT validation and the shared LRU
cache remain active. Interpolation may load samples from one, two or four tiles without exposing tile
boundaries to callers.

## Elevation sampling

`ElevationSampler` offers two modes:

```java
ElevationSampler sampler = new ElevationSampler(atlas, "elevation");
OptionalInt nearest = sampler.sampleNearestMetres(latitude, longitude);
OptionalDouble smooth = sampler.sampleBilinearMetres(latitude, longitude);
```

Nearest-neighbour sampling selects the closest stored raster sample. It is useful for diagnostics and
for proving exact source values.

Bilinear sampling blends the four surrounding elevation samples according to their horizontal and
vertical distance. It is the preferred smooth elevation result for later terrain generation. At atlas
edges, the final valid sample is reused rather than reading outside the declared grid.

Elevation results are optional because `-32768` represents no-data. Nearest sampling returns empty when
the selected sample is no-data. Bilinear sampling returns empty when any sample with a non-zero weight
is no-data. A no-data neighbour with zero weight does not invalidate an exact sample lookup.

## Land-mask sampling

`LandMaskSampler` uses nearest-neighbour classification:

```java
LandMaskSampler sampler = new LandMaskSampler(atlas, "land_mask");
boolean land = sampler.isLand(latitude, longitude);
```

A land mask is categorical rather than continuous, so averaging four boolean samples would not have a
well-defined meaning. More sophisticated coastline treatment can be added later without changing the
manifest or tile reader.

## Standalone commands

Query elevation from a compiled atlas directory:

```bash
./gradlew :modules:atlas-compiler:run \
  --args="sample-elevation <atlas-directory> <layer-id> <latitude> <longitude> bilinear" \
  --no-configuration-cache
```

Use `nearest` instead of `bilinear` to inspect the closest stored value. Output is one of:

```text
elevation_metres=123.5
elevation_metres=no-data
```

Query the land mask:

```bash
./gradlew :modules:atlas-compiler:run \
  --args="sample-land <atlas-directory> <layer-id> <latitude> <longitude>" \
  --no-configuration-cache
```

Output is `land=true` or `land=false`.

## Step 3 guarantees

- geographic coordinates are checked against manifest bounds
- each layer uses its own declared raster dimensions
- global samples are routed to the correct tile and local coordinates
- bilinear elevation reads work across tile boundaries
- all reads use the atlas-wide cache
- corrupt, missing or manifest-incompatible tiles retain the Step 2 failure behavior
- no GIS library is required at runtime

## Deferred work

This step does not define pixel-centre versus pixel-edge import rules, create real source datasets,
perform GDAL processing, generate a full raster tile set, select regional overrides, wrap longitude,
compress polar regions or convert Minecraft block coordinates. Those remain later Phase 1 and Phase 2
work.
