# Phase 1 Step 5 — Full-raster tiling

## Goal

Compile complete normalized elevation and land-mask rasters into an openable deterministic atlas,
including partial edge tiles, without requiring the entire raster to fit in memory.

## Completed scope

- row-major signed-int16 little-endian elevation input
- row-major byte-per-sample land-mask input
- independent layer raster dimensions
- exact source-size validation
- bounded-memory tile extraction
- deterministic layer/Y/X traversal
- manifest path-template rendering
- elevation no-data padding for partial edges
- water padding for partial land-mask edges
- canonical manifest output
- rollback of partial output after compilation failure
- refusal to overwrite an existing path
- standalone `compile-raster-atlas` command
- byte-for-byte determinism tests
- round-trip runtime and geographic sampling tests

## Exit evidence

Synthetic rasters with dimensions that are not multiples of the tile size compile into a complete atlas.
The runtime opens every layer, reads padded edge tiles and samples the final real cells. Two independent
compilations produce identical relative file sets and bytes.

## Deferred

The compiler input is deliberately a normalized raw-raster contract. GIS format decoding, reprojection,
resampling, source-data selection, multiresolution generation and regional overrides remain later
Phase 1 work.
