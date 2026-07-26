# Northern Europe detailed atlas

Phase 1 Step 8 defines the first release-scale regional atlas. It covers the master plan's complete
Northern Europe vertical-slice extent and uses the same manifest, OTAT tile, cache and geographic
sampling contracts already proven by the synthetic and Bergen fixtures.

The resulting archive is intentionally generated as a workflow or release artifact rather than committed
to Git. Source control retains the profiles, pinned source lock, builder, tests and reproducible commands.

## Geographic contract

| Property | Value |
| --- | --- |
| Atlas id | `northern-europe-detailed-v1` |
| West/east sample centres | 25°W / 45°E |
| South/north sample centres | 54°N / 72°N |
| Resolution | 15 arc-seconds |
| Elevation grid | 16,801 × 4,321 samples |
| Land-mask grid | 16,801 × 4,321 samples |
| Samples per layer | 72,597,121 |
| Tile size | 256 × 256 samples |
| Tile grid | 66 × 17 |
| Tiles per layer | 1,122 |
| Projection | EPSG:4326 equirectangular |

The resolution is approximately 463 metres north-to-south. East-to-west spacing decreases with
latitude, from roughly 272 metres at 54°N to roughly 143 metres at 72°N. This is a regional prototype
resolution, not the final limit for later country or fjord overrides.

## Reviewed sources

### Elevation

- Dataset: NOAA NCEI ETOPO 2022 15 Arc-Second Surface Elevation
- Release: 2022 v1
- CRS: EPSG:4326
- Vertical datum: EGM2008 height
- Licence: CC0-1.0
- Citation: NOAA National Centers for Environmental Information. 2022: ETOPO 2022 15 Arc-Second
  Global Relief Model. DOI `10.25921/fd45-gt74`.

Twelve official 15° × 15° GeoTIFF tiles cover the regional bounds, including the half-sample-expanded
processing extent. The source lock records every URL and SHA-256 digest. ETOPO surface elevation
includes both land elevation and ocean bathymetry, but the current atlas schema keeps these concepts
separate. The builder therefore applies the land mask and writes valid ocean samples as zero in the
elevation layer. A dedicated bathymetry layer remains a later milestone.

The tile names identify each source tile's southwest corner. The required rows are `N45` and `N60`, with
longitude columns `W030`, `W015`, `E000`, `E015`, `E030` and `E045` in each row.

### Land mask

- Dataset: Natural Earth 1:10m Land
- Version: 5.1.1
- Licence: public domain
- Processing: polygons are rasterized to byte value `1`; all other samples are `0`

The same reviewed Natural Earth source used by the Bergen fixture is reused here. It is sufficient for
regional proof-of-concept terrain, but it omits some small skerries and does not represent inland water.

## Profiles

`atlas/profiles/northern-europe-detailed-v1.json` is the release profile.

`atlas/profiles/northern-europe-detailed-smoke.json` is a one-degree Icelandic profile. It uses the same
15-arc-second source type, masking, normalization, compilation and archive code while remaining small
enough for normal CI.

Both profiles define inclusive first/last sample-centre bounds. The builder expands these by half a
sample before invoking GDAL, matching the runtime coordinate conversion exactly.

## Planning

```bash
python scripts/gis/build_northern_europe_detailed_atlas.py plan
```

The plan reports dimensions, raw byte counts, tile rows/columns, source count, source-lock status and
exact GDAL pixel-edge bounds without downloading data.

Plan the CI smoke profile:

```bash
python scripts/gis/build_northern_europe_detailed_atlas.py \
  --profile atlas/profiles/northern-europe-detailed-smoke.json \
  plan
```

## Source lock resolution

Source downloads are cached outside the output directory. To audit or deliberately refresh a lock:

```bash
python scripts/gis/build_northern_europe_detailed_atlas.py \
  resolve-lock build/northern-europe-sources.lock.json \
  --cache build/atlas-source-cache
```

Review every changed URL and digest before replacing the checked-in lock. A normal build rejects any
source without a pinned digest and rejects downloaded bytes that do not match the lock.

## Building the release atlas

```bash
python scripts/gis/build_northern_europe_detailed_atlas.py \
  build build/northern-europe-detailed-v1 \
  --cache build/atlas-source-cache
```

The build performs these stages:

1. Verify and reuse or download all pinned source files.
2. Build a VRT mosaic from the twelve ETOPO tiles.
3. Rasterize Natural Earth land polygons onto the exact target grid.
4. Align ETOPO to the target sample-centre grid with exact coordinate transformation.
5. Mask ocean elevation to zero using the land mask.
6. Run the existing GDAL normalization tool.
7. Run `compile-raster-atlas` to create both OTAT layers.
8. Copy previews, normalization reports, source locks and attribution into the atlas.
9. Write fixture-wide SHA-256 checksums.
10. Create and verify a deterministic ZIP archive.

The output root contains:

```text
atlas/
normalized/
workspace/
northern-europe-detailed-v1.zip
```

The `workspace` and `normalized` directories are build intermediates. The `atlas` directory and ZIP are
the distributable outputs.

## Verification

Verify either the atlas directory or release archive without network access or GDAL:

```bash
python scripts/gis/build_northern_europe_detailed_atlas.py \
  verify build/northern-europe-detailed-v1/northern-europe-detailed-v1.zip
```

Verification checks:

- fixture-wide SHA-256 hashes;
- exact file set;
- manifest id and bounds;
- both layer dimensions and tile sizes;
- 1,122 elevation tiles;
- 1,122 land-mask tiles;
- safe ZIP extraction.

Supplying `--compare <existing.zip>` during a build additionally requires byte-for-byte deterministic
archive equality.

## CI and release workflow

Normal Linux and Windows CI validate profile parsing, source-lock rules, grid geometry, deterministic ZIP
metadata and both profile plans. A real-data Linux smoke job downloads the pinned Iceland ETOPO tile plus
Natural Earth, then normalizes, compiles and verifies the one-degree Icelandic atlas.

`.github/workflows/build-northern-europe-atlas.yml` is manually triggered for the release profile. It uses
an external source cache, builds the full archive and uploads the atlas directory, ZIP, resolved source
metadata and logs as workflow artifacts.

## Limitations

- ETOPO 15 arc-seconds is substantially coarser than Copernicus GLO-90.
- Natural Earth does not preserve every small Scandinavian island or skerry.
- Inland water is currently classified as land by this mask.
- Bathymetry is deliberately discarded from the elevation layer and will become a separate layer.
- Country-scale and fjord-scale overrides remain future multiresolution work.
- The release artifact is a Phase 1 terrain prototype, not a navigation or scientific analysis product.
