# Runtime atlas selection and fallback

Phase 1 Step 10 combines multiple opened atlas directories into one deterministic sampling stack. The
primary production use is to prefer `northern-europe-detailed-v1` within its coverage and use
`global-coarse-v1` elsewhere.

## API

```java
AtlasDirectory global = AtlasDirectory.open(globalAtlasDirectory);
AtlasDirectory regional = AtlasDirectory.open(northernEuropeAtlasDirectory);
AtlasStack stack = AtlasStack.of(global, regional);

AtlasStack.ElevationSample elevation =
        stack.sampleBilinearElevationMetres(latitude, longitude).orElseThrow();
AtlasStack.LandMaskSample landMask = stack.sampleLandMask(latitude, longitude).orElseThrow();
```

Each result includes the value and the `atlasId` that supplied it. This makes selection behavior visible
to diagnostics, tests, and later Minecraft integration.

## Per-layer ranking

Elevation and land-mask candidates are ranked independently. An atlas may therefore supply detailed
elevation while a different atlas supplies the land mask at the same coordinate.

Candidate resolution is calculated from the angular sample spacing declared by the manifest bounds and
the layer grid dimensions:

```text
longitude spacing = (east - west) / (grid width - 1)
latitude spacing  = (north - south) / (grid height - 1)
rank              = longitude spacing × latitude spacing
```

The smallest angular sample area is preferred. Constructor order is the stable tie-breaker when two
layers have equal resolution. Atlas identifiers must be unique within one stack, and an individual atlas
must not declare multiple layers of the same type because that would make selection ambiguous.

## Sample-cell coverage

Manifest bounds describe the centres of the first and last samples. Selection coverage extends half a
sample beyond those centres and is clipped to legal world coordinates:

```text
west edge  = max(-180°, west sample  - longitude spacing / 2)
east edge  = min( 180°, east sample  + longitude spacing / 2)
south edge = max( -90°, south sample - latitude spacing  / 2)
north edge = min(  90°, north sample + latitude spacing  / 2)
```

A coordinate inside that half-cell edge coverage selects the atlas. Before the existing sampler is
called, the coordinate is clamped back to the first or last stored sample centre. This gives the global
cell-centred atlas complete world coverage and avoids gaps of half a cell around regional atlas edges.

## Coordinate normalization

The stack applies one normalization policy before selection:

- finite latitude and longitude are required;
- latitude is clamped to `[-90°, 90°]`;
- longitude is wrapped to `[-180°, 180°)`;
- `180°`, `-180°`, and longitude values differing by complete turns select the same antimeridian side;
- a global layer whose half-cell edges reach both `-180°` and `180°` covers every normalized longitude.

The global coarse atlas then clamps an antimeridian or polar coordinate to its nearest stored sample
centre. Regional atlases continue to use ordinary non-wrapping bounds.

## Fallback behavior

Candidates are tried in ranked order. The stack continues to the next covering candidate when:

- the preferred atlas does not declare that layer type;
- an elevation sample is no-data;
- a required preferred tile is missing;
- a preferred tile is corrupt or incompatible;
- another `IOException` prevents the preferred sample from being read.

If a lower-resolution candidate succeeds, its result is returned normally with its source `atlasId`.
When no candidate covers the coordinate, or all covering elevation candidates contain no-data, the result
is empty. When every covering candidate fails with an I/O error, the stack throws one combined
`IOException`; the first ranked failure is the cause and later failures are suppressed.

Programming errors such as non-finite coordinates, duplicate atlas identifiers, and ambiguous duplicate
layer types are rejected immediately and do not trigger fallback.

## Determinism

Selection depends only on:

1. normalized coordinates;
2. manifest bounds and grid dimensions;
3. layer type;
4. angular sample area;
5. constructor order for exact ties;
6. whether each candidate can produce a valid sample.

Cache state, directory iteration order, and operating system path behavior do not affect which atlas is
selected.

## Tested cases

The synthetic selection suite verifies:

- regional elevation preference inside overlap;
- global fallback outside regional coverage;
- independent elevation and land-mask selection;
- elevation no-data fallback;
- missing and corrupt preferred tile fallback;
- combined errors when every covering tile is unreadable;
- half-cell coverage at all regional edges;
- stable constructor-order ties;
- antimeridian wrapping for `-180°`, `180°`, and `540°`;
- north and south polar clamping;
- duplicate identifier and non-finite coordinate rejection.

## Current limitations

- Atlas manifests cannot yet describe a regional coverage rectangle that itself crosses the
  antimeridian.
- Selection is based on rectangular layer coverage, not per-tile availability metadata; unavailable
  preferred tiles are discovered lazily during sampling and then fall back.
- The stack selects one atlas per sample. Cross-atlas blending at regional boundaries is not part of
  Phase 1.
- Archive discovery, installation, and lifecycle management remain responsibilities of the later
  Minecraft/runtime integration layer.
