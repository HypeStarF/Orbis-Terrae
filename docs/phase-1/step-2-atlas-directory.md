# Phase 1 Step 2: atlas directory reading

## Goal

Provide the runtime boundary between a compiled atlas directory and later geographic sampling code.
The implementation must open one atlas root, load its strict manifest, expose typed layer handles,
resolve declared tile paths, read and validate requested OTAT files and reuse decoded tiles through a
bounded cache.

## Completed in this step

- `AtlasDirectory` opens a real directory containing `atlas-manifest.json`.
- `AtlasLayer` exposes layer metadata, tile-grid dimensions, safe paths and typed reads.
- `AtlasTileStore` resolves, reads, validates and caches OTAT tiles across all layers.
- `AtlasAccessException` distinguishes unsafe or manifest-incompatible storage from ordinary missing
  files and OTAT corruption.
- Tile paths are checked before and after symbolic-link resolution.
- Loaded tile headers must match the manifest format version, tile size, layer type and encoding.
- Missing, corrupt and incompatible tiles are not cached.
- Runtime directory behavior is documented in `docs/atlas/runtime-directory.md`.

## Deliberate decisions

- Atlas opening is lazy. It validates the root and manifest but does not scan every declared tile.
- The LRU budget is shared across all layers so the configured limit is an atlas-wide upper bound.
- The final partial tile is counted with ceiling division; future full-raster compilation must define
  deterministic padding for samples outside the declared grid extent.
- Cache clearing removes decoded values but retains cumulative cache statistics.

## Deferred

This step does not perform geographic sampling, interpolation, cross-tile sample reads, GeoTIFF/GDAL
processing, complete-atlas verification or Minecraft terrain generation.

## Tests

The test suite creates synthetic atlas directories and verifies:

- manifest loading and stable layer order
- tile-column and tile-row calculations
- safe path rendering
- typed elevation and land-mask reads
- cache hits and atlas-wide LRU eviction
- missing and out-of-range tiles
- missing roots, non-directory roots and missing manifests
- tile layer-type and tile-size mismatches
- CRC corruption propagation
- cache clearing
