# Atlas directory runtime

Step 2 of Phase 1 turns the manifest and individual OTAT reader into a usable on-disk atlas.
The runtime opens an atlas directory, loads `atlas-manifest.json`, exposes the declared layers and
loads requested tiles through one bounded cache shared by the complete atlas.

## Directory layout

A compiled atlas has one manifest at its root and tile files below it. The exact tile paths come from
each layer's manifest `pathTemplate`.

```text
atlas-root/
├── atlas-manifest.json
└── layers/
    ├── elevation/0/0/0.otat
    └── land-mask/0/0/0.otat
```

The runtime does not scan every tile when the atlas opens. A global atlas may contain many thousands
of files, so startup validates the directory and manifest only. Each tile is then checked lazily when
it is first requested.

## Opening an atlas

```java
AtlasDirectory atlas = AtlasDirectory.open(Path.of("atlas-root"), 64);
AtlasLayer elevation = atlas.requireElevationLayer("elevation");
ElevationTile tile = elevation.readElevationTile(0, 0);
short metres = tile.elevationMetres(10, 20);
```

The cache limit is the maximum number of decoded tiles retained across all layers. The overload
without an explicit limit uses `AtlasDirectory.DEFAULT_MAXIMUM_CACHED_TILES`.

## Runtime objects

- `AtlasDirectory` owns the root path, parsed manifest, declared layer handles and shared tile cache.
- `AtlasLayer` exposes manifest metadata, tile-grid dimensions, safe tile paths and typed tile reads.
- `AtlasTileStore` is internal. It resolves paths, reads OTAT files, validates headers and manages the
  shared LRU cache.
- `AtlasAccessException` reports unsafe directory entries or tiles that disagree with the manifest.

## Tile-grid dimensions

A layer's number of tile columns and rows is calculated with ceiling division:

```text
tile columns = ceil(gridWidthSamples / tileSize)
tile rows    = ceil(gridHeightSamples / tileSize)
```

The final tile may therefore contain padding samples when a grid dimension is not a multiple of the
tile size. The full-raster compiler step must later define and write that padding consistently.

## Validation on first read

A requested tile must:

1. Use coordinates inside the layer's declared tile grid.
2. Resolve to a path inside the atlas root.
3. Exist and be a regular file.
4. Remain inside the atlas root after symbolic links are resolved.
5. Pass the OTAT magic, version, payload-length, CRC32 and payload checks.
6. Match the manifest's format version, tile size, layer type and encoding.
7. Decode to the expected Java tile type.

A successful tile is cached only after all validation passes. Missing, corrupt or incompatible tiles
are never placed in the cache.

## Typed access

`requireElevationLayer` and `requireLandMaskLayer` validate the manifest layer type before returning a
handle. The layer then provides `readElevationTile` or `readLandMaskTile`. Generic `readTile` remains
available for code that deliberately handles both types.

## Cache behavior

The cache is shared across the atlas rather than allocated separately per layer. With a two-entry
cache, one elevation tile and one land-mask tile use both entries. Loading a third distinct tile
evicts the least recently used entry regardless of its layer.

`cacheStats()` reports current size, maximum entries, hits, misses and evictions. `clearCache()` removes
all decoded tiles while retaining the accumulated statistics.

## Scope boundary

This step provides directory and tile access only. It does not yet convert latitude/longitude into a
sample, interpolate elevation, read across tile boundaries, import GeoTIFF data or generate Minecraft
terrain. Those remain later Phase 1 and Phase 2 work.
