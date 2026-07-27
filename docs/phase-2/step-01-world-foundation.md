# Phase 2 Step 1: world foundation

This step begins the Basic Earth Dimension phase by locking down the settings that later generator codecs
will consume.

## Added contracts

### Built-in world profiles

`WorldProfiles` defines the first two supported terrain-alpha presets:

| Profile | Horizontal scale | Projected size | Dimension range | Sea level |
| --- | ---: | ---: | ---: | ---: |
| Global Compact | 2,000 m/block | 20,038 x 10,002 blocks | -64 through 319 | 63 |
| Global Survival | 1,000 m/block | 40,076 x 20,004 blocks | -64 through 319 | 63 |

Projected dimensions are rounded upward to even block counts so the equirectangular Earth remains centred
around Minecraft X/Z zero.

### Coordinate mapping

`EarthCoordinateMapper` establishes the Phase 2 projection rules:

- Minecraft X maps to longitude.
- Minecraft Z maps inversely to latitude, so negative Z is north.
- X zero and Z zero represent longitude zero and the equator.
- East-west coordinates normalize into the finite projected width.
- Latitude is clamped to the finite north-south projection until polar-cap behaviour is implemented in
  Phase 11.

Longitude wrapping is part of atlas sampling from the first terrain prototype. Player transfer and visual
seam handling remain Phase 11 work.

### Vertical transformation

`WorldProfile.PiecewiseLinearVerticalCurve` provides a strict, deterministic nonlinear transformation from
real metres to blocks relative to sea level. Control points must be finite and strictly ordered. Generated
heights are clamped to the configured dimension range.

The initial curves reserve space for both bathymetry and mountain prominence while remaining inside the
vanilla-height prototype dimension. These values are provisional and may change before Phase 2 closes;
existing worlds remain protected because the exact points are copied into their manifest.

### Immutable world manifest

World manifest schema v1 records:

- generator version;
- atlas version;
- projection;
- complete profile snapshot;
- seed;
- deterministic SHA-256 configuration hash;
- creation instant.

The JSON codec rejects unknown fields, null primitive values, unsupported schema versions, unsupported
projections, malformed hashes, and trailing JSON. The exact profile values are stored rather than only a
preset name, preventing future preset edits from silently changing existing worlds.

## Validation

`Phase2FoundationTest` covers:

- exact built-in projected dimensions;
- profile lookup;
- geographic round-trips;
- east-west normalization;
- finite latitude bounds;
- vertical interpolation, extrapolation, and dimension clamping;
- deterministic manifest hashes;
- strict manifest JSON round-trips;
- rejection of unknown or invalid values.

## Deferred to Step 2

This step does not yet register a Minecraft `BiomeSource`, `ChunkGenerator`, or dimension. Step 2 will add
the codec registration boundary and consume these tested profile and manifest contracts.
