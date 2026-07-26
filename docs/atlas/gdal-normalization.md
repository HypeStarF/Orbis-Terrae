# GDAL raster normalization

Phase 1 Step 6 defines the reproducible boundary between source GIS datasets and the Java atlas
compiler. GDAL is an external build-time dependency only. The Minecraft mod and `atlas-api` runtime
continue to have no GIS dependency.

The workflow accepts one elevation raster and one categorical land-mask raster, reprojects and
resamples both onto explicitly registered EPSG:4326 grids, writes the exact raw files consumed by
`compile-raster-atlas`, creates inspectable previews, and records checksums and provenance.

This step does not select or redistribute an official dataset. Every source still requires a separate
licence, attribution and redistribution review before it can be bundled.

## Requirements

- Python 3.11 or newer
- GDAL 3.8 or newer
- `gdalwarp`, `gdal_translate` and `gdalinfo` on `PATH`
- Source rasters with georeferencing and a CRS GDAL can identify

The tool uses only the Python standard library and invokes GDAL command-line programs. NumPy and the
GDAL Python bindings are not required.

The process disables PROJ network downloads, requests one GDAL thread, disables source overviews, and
uses exact coordinate transformation. Required projection grids must therefore already be installed
locally.

## Job file

Jobs follow `atlas/schemas/raster-normalization-job.schema.json`. The checked-in
`atlas/profiles/gdal-normalization-example.json` is a placeholder. Copy it under `atlas/local`, replace
both source paths and every provenance placeholder, and keep downloaded source data under the ignored
`atlas/local` tree.

Important fields:

- `atlas.bounds` are inclusive coordinates of the first and last sample centres.
- `widthSamples` and `heightSamples` are exact normalized raster dimensions.
- `sourceNoData` overrides intrinsic source no-data when supplied.
- elevation defaults to bilinear resampling.
- land mask defaults to nearest-neighbour resampling.
- `landValues` lists source byte values that mean land; every other value becomes water.
- each layer records source, version, licence, attribution, URL, retrieval date and processing.

Do not distribute an atlas while any provenance placeholder remains.

## Pixel registration

The runtime sampler treats manifest bounds as inclusive sample-centre coordinates:

```text
sampleX = (longitude - west) / (east - west) * (width - 1)
sampleY = (north - latitude) / (north - south) * (height - 1)
```

GDAL output extents describe pixel edges. The tool therefore calculates:

```text
xResolution = (east - west) / (width - 1)
yResolution = (north - south) / (height - 1)

edgeWest  = west  - xResolution / 2
edgeEast  = east  + xResolution / 2
edgeSouth = south - yResolution / 2
edgeNorth = north + yResolution / 2
```

It passes those edge bounds together with the exact output dimensions to `gdalwarp`. The first and last
pixel centres consequently match the manifest bounds without a half-pixel shift.

Inspect a job without running GDAL:

```bash
python scripts/gis/normalize_atlas.py plan \
  atlas/profiles/gdal-normalization-example.json
```

## Normalize source rasters

```bash
python scripts/gis/normalize_atlas.py normalize \
  atlas/local/northern-europe-job.json \
  atlas/local/normalized/northern-europe
```

Use `--gdal-bin <directory>` when GDAL is not on `PATH`. Use `--keep-intermediate` only for debugging.
The output path must not already exist. Failed runs remove incomplete output.

A successful directory contains:

```text
atlas-manifest.json
normalization-report.json
checksums.sha256
elevation.raw
land-mask.raw
elevation-preview.pgm
land-mask-preview.pgm
```

### Elevation contract

- row-major, northern row first
- signed 16-bit integer metres
- little-endian on every operating system
- `-32768` reserved for no-data

GDAL's ENVI export may be little- or big-endian. The tool reads the ENVI header and converts the payload
explicitly before writing `elevation.raw`.

### Land-mask contract

- row-major, northern row first
- one byte per sample
- `0` water and `1` land

Only configured `landValues` become `1`, ensuring the Java compiler never receives an unexpected
categorical value.

### Previews and provenance

The previews use binary PGM (`P5`). Elevation uses `0` for no-data and scales valid values into
`1..255`; the land mask uses `0` for water and `255` for land.

`normalization-report.json` records the GDAL version, disabled network access, source hashes, exact grid
geometry, sanitized warp commands, selected `gdalinfo` data, raw hashes and statistics, and preview
hashes. `checksums.sha256` protects every compiler input and report artifact.

Verify an output directory:

```bash
python scripts/gis/normalize_atlas.py verify \
  atlas/local/normalized/northern-europe
```

Verification checks hashes, raw byte lengths against manifest dimensions, required layer IDs, and the
land-mask `0`/`1` contract.

## Compile OTAT tiles

```bash
./gradlew :modules:atlas-compiler:run \
  --args="compile-raster-atlas atlas/local/normalized/northern-europe/atlas-manifest.json atlas/local/normalized/northern-europe/elevation.raw atlas/local/normalized/northern-europe/land-mask.raw atlas/local/atlases/northern-europe" \
  --no-configuration-cache
```

On Windows, use `python` with the same arguments and replace `./gradlew` with `.\gradlew.bat`.

## Resampling policy

- Use `bilinear` for ordinary elevation reprojection.
- Use `near` for land/water categories.
- `average` may be suitable for deliberate elevation downsampling but changes terrain character.
- Cubic and Lanczos methods can overshoot near sharp terrain or no-data boundaries.
- Never use continuous interpolation for categorical land masks.

The tool passes `-ovr NONE`, preventing use of source overviews made with an unknown resampling method,
and `-et 0`, preventing approximation differences caused by warp chunk layout.

## Vertical datum and source review

Reprojection to EPSG:4326 changes horizontal coordinates. It does not prove that elevation values use
the intended vertical datum. Before accepting a dataset:

1. identify whether heights are ellipsoidal, orthometric or referenced to another datum;
2. record the datum and any transformation;
3. verify known coastal and mountain elevations;
4. keep `-32768` exclusively for no-data;
5. inspect the generated previews and sampled coordinates.

The tool does not remove vegetation, buildings or other digital-surface-model artefacts. Those are
source-specific processing operations and must be recorded separately.

## Deferred work

This step does not download or approve datasets, construct a coastline from vectors, correct vertical
datums, remove surface artefacts, choose official regional resolution, or create multiresolution and
regional-override layers. The next data step should run this workflow on a small legally reviewable
Scandinavian source area.
