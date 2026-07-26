# Full-raster atlas compilation

Phase 1 Step 5 converts complete normalized elevation and land-mask rasters into a manifest plus every
OTAT tile required by the runtime. This is the bridge between single-tile prototypes and the later GDAL
normalization workflow.

## Input contract

The compiler accepts four paths:

```text
manifest template
normalized elevation raster
normalized land-mask raster
new output directory
```

The manifest defines geographic bounds, layer IDs, raster dimensions, tile sizes, zoom levels, path
templates and provenance. Full-raster compilation currently requires exactly one elevation layer and
one land-mask layer.

### Elevation raster

- signed 16-bit integers
- little-endian byte order
- one value per sample
- row-major order
- northern row first
- exactly `gridWidthSamples * gridHeightSamples * 2` bytes
- `-32768` is the elevation no-data value

### Land-mask raster

- one byte per sample
- `0` means water
- `1` means land
- row-major order
- northern row first
- exactly `gridWidthSamples * gridHeightSamples` bytes

The two layers may use different raster dimensions. This preserves the manifest design in which layers
have independent resolutions.

## Command

```bash
./gradlew :modules:atlas-compiler:run \
  --args="compile-raster-atlas <manifest.json> <elevation.raw> <land-mask.raw> <output-directory>" \
  --no-configuration-cache
```

The output directory must not already exist. This prevents an incomplete or mismatched atlas from being
silently combined with older files.

## Tile traversal

Tiles are written deterministically:

1. manifest layer order
2. tile Y from north to south
3. tile X from west to east

Source rows are read through positional file-channel reads. The compiler keeps only one tile-sized
sample buffer in memory instead of loading an entire regional or global raster.

## Partial edge tiles

Raster dimensions do not need to be exact multiples of the tile size. Every declared tile remains a
full square OTAT tile:

- elevation samples outside the real raster are padded with `-32768`
- land-mask samples outside the real raster are padded with water

The manifest retains the true raster width and height, so geographic sampling never exposes padded
cells.

## Failure behavior

Before creating output, the compiler validates:

- both source paths are regular files
- source byte lengths exactly match their layer dimensions
- exactly one layer of each supported type is declared
- the output path does not already exist

If reading, validation or tile writing fails after output creation, the partial atlas directory is
removed. Invalid land-mask values report their global sample coordinates.

## Determinism

For identical manifest and raster bytes, compilation produces:

- the same relative file set
- the same canonical LF manifest bytes
- the same OTAT header and payload bytes
- the same per-tile CRC32 values

The tests compile independent output directories and compare every file byte-for-byte.

## Deferred work

This step intentionally assumes normalized raw rasters. It does not yet:

- open GeoTIFF or other GIS formats
- reproject source data
- resample source pixels
- define a GDAL container or command workflow
- select real-world datasets or redistribution licences
- build multiresolution pyramids
- create regional override layers

Those are addressed by the following Phase 1 steps.
