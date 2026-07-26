# Global coarse atlas

Phase 1 Step 9 adds the low-detail world atlas required by the project's progressive-fidelity
architecture. It is the fallback atlas for locations that do not have a higher-detail regional layer.
The following step will add runtime selection between this atlas and the Northern Europe detailed atlas.

## Geographic contract

| Property | Value |
| --- | --- |
| Atlas id | `global-coarse-v1` |
| Pixel-edge coverage | 180°W–180°E, 90°S–90°N |
| First/last longitude sample centres | -179.95833333333334° / 179.95833333333334° |
| First/last latitude sample centres | -89.95833333333333° / 89.95833333333333° |
| Resolution | 300 arc-seconds / 5 arc-minutes |
| Grid per layer | 4,320 × 2,160 samples |
| Samples per layer | 9,331,200 |
| Tile size | 256 × 256 samples |
| Tile grid | 17 × 9 |
| Tiles per layer | 153 |
| Projection | EPSG:4326 equirectangular |

The atlas uses a cell-centred global grid. Its sample centres sit half a pixel inside the geographic
edges, so it covers the whole world without storing a duplicate antimeridian column. Longitude wrapping
and polar clamping belong to the runtime atlas-selection step rather than the raster itself.

At the equator, five arc-minutes is approximately 9.3 km. East-west spacing decreases toward the poles.
This is deliberately a fallback representation; regional atlases provide higher fidelity.

## Reviewed sources

### Elevation

- Dataset: NOAA NCEI ETOPO 2022 60 Arc-Second Surface Elevation
- File: `ETOPO_2022_v1_60s_N90W180_surface.tif`
- Release: 2022 v1
- CRS: EPSG:4326
- Vertical datum: EGM2008 height
- Licence: CC0-1.0
- Citation: NOAA National Centers for Environmental Information. 2022: ETOPO 2022 Global Relief Model.
  DOI `10.25921/fd45-gt74`.

ETOPO contains topography and bathymetry. The current `elevation` layer represents land elevation, so
bathymetric values are masked to zero over water. A dedicated bathymetry layer remains a later milestone.

### Land mask

- Dataset: Natural Earth 1:10m Land
- Version: 5.1.1
- Licence: public domain
- Processing: polygons are rasterized to `1`; all other cells are `0`

The source lock records every URL and SHA-256 digest. Original source rasters are cached during builds but
are not committed or redistributed by the repository.

## Shared build engine

`scripts/gis/build_global_coarse_atlas.py` is a global profile for the existing deterministic elevation
atlas build engine. It replaces only the source contract, source preparation and provenance while reusing
profile validation, download locking, normalization, OTAT compilation, checksum verification and stable
ZIP generation.

Plan the atlas without downloading data:

```bash
python scripts/gis/build_global_coarse_atlas.py plan
```

Build from pinned sources:

```bash
python scripts/gis/build_global_coarse_atlas.py \
  build build/global-coarse-v1 \
  --cache build/global-atlas-source-cache
```

Verify an atlas directory or archive offline:

```bash
python scripts/gis/build_global_coarse_atlas.py \
  verify build/global-coarse-v1/global-coarse-v1.zip
```

## Build stages

1. Verify or download the pinned global ETOPO GeoTIFF and Natural Earth archive.
2. Rasterize Natural Earth polygons to the exact 4,320 × 2,160 global grid.
3. Downsample ETOPO from 60 to 300 arc-seconds with average resampling.
4. Mask ocean elevations to zero.
5. Run the existing normalization workflow.
6. Compile elevation and land-mask rasters into OTAT tiles.
7. Copy source locks, provenance, previews and reports into the atlas.
8. Verify all checksums, dimensions and 306 total OTAT tiles.
9. Create a deterministic ZIP archive.

## CI and release workflow

Normal Linux and Windows CI parse the profile, validate the source contract and run the global builder's
unit tests without downloading the approximately 444 MB source GeoTIFF.

`.github/workflows/build-global-coarse-atlas.yml` is manually triggered for a full artifact build. It
uses a source cache and uploads the atlas directory, deterministic ZIP, plan, build log and archive hash.

## Limitations

- Five arc-minutes cannot preserve small islands, narrow straits or local relief.
- Natural Earth omits some very small islands and does not represent inland water in this mask.
- Ocean elevation is intentionally zero; bathymetry is not yet exposed.
- Exact antimeridian wrapping and polar clamping are runtime responsibilities.
- This atlas is a Phase 1 fallback, not a navigation or scientific-analysis product.
