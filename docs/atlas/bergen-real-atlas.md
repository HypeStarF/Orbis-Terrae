# Bergen real-data atlas

Phase 1 Step 7 adds the first atlas built from reviewed external geographic data. The fixture covers
Bergen and nearby western-Norwegian coast and fjords. It is deliberately small enough to remain in the
source repository while exercising real mountains, islands, open sea, coastline complexity and internal
tile boundaries.

## Geographic contract

| Property | Value |
| --- | --- |
| Atlas id | `bergen-real-v1` |
| West/east sample centres | 4.75°E / 5.75°E |
| South/north sample centres | 60.2°N / 60.8°N |
| Elevation grid | 256 × 256 samples |
| Land-mask grid | 256 × 256 samples |
| Tile size | 64 × 64 samples |
| Tile grid | 4 × 4 tiles per layer |
| Projection | EPSG:4326 equirectangular |
| Observed elevation range | 0–946 metres |

The manifest bounds are coordinates of the first and last sample centres. The acquisition builder uses
the same half-pixel edge expansion defined by the GDAL normalization workflow.

## Reviewed sources

### Elevation

- Dataset: Copernicus DEM GLO-90
- Distribution: public Cloud Optimized GeoTIFFs from the Registry of Open Data on AWS
- Source tiles: `N60E004` and `N60E005`
- Source release: 2021 AWS COG conversion
- Horizontal CRS: EPSG:4326
- Vertical reference: EGM2008 orthometric height
- Product type: digital surface model, not a bare-earth terrain model

Required adapted-product notice:

> produced using Copernicus WorldDEM-90 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH
> 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved

This notice is preserved in the atlas manifest and `ATTRIBUTION.md`.

### Land mask

- Dataset: Natural Earth 1:10m Land
- Version: 5.1.1
- Licence: public domain
- Processing: land polygons are rasterized to `1`; all other cells are `0`

Natural Earth attribution is optional, but the fixture records `Made with Natural Earth` for clarity.

## Committed fixture

The repository stores `atlas/test-fixtures/bergen-real-v1.zip`. It is a deterministic ZIP with fixed
entry timestamps, sorted paths and consistent compression. The bundle contains:

- the canonical manifest;
- sixteen elevation OTAT tiles;
- sixteen land-mask OTAT tiles;
- required attribution;
- normalization and source-lock metadata;
- elevation and land-mask PGM previews;
- fixture-wide SHA-256 checksums.

Tests extract the bundle to a temporary directory before opening it through `AtlasDirectory`. The ZIP
transport does not change the runtime atlas directory format.

## Reproducible build

The pinned acquisition inputs are listed in
`atlas/datasets/bergen-real-v1/sources.lock.json`. The lock stores each HTTPS URL and SHA-256 digest.
Downloaded source files remain temporary and are never committed.

Plan the build:

```bash
python scripts/gis/build_bergen_real_atlas.py plan
```

Build from the pinned sources:

```bash
python scripts/gis/build_bergen_real_atlas.py build \
  build/bergen-real-v1 \
  --require-locked
```

Compare a fresh build byte-for-byte with the checked-in archive:

```bash
python scripts/gis/build_bergen_real_atlas.py build \
  build/bergen-real-v1 \
  --require-locked \
  --compare atlas/test-fixtures/bergen-real-v1.zip
```

Verify the committed archive without network access or GDAL:

```bash
python scripts/gis/build_bergen_real_atlas.py verify \
  atlas/test-fixtures/bergen-real-v1.zip
```

## Build stages

1. Download two pinned Copernicus GLO-90 COG tiles.
2. Download the pinned Natural Earth land archive.
3. Verify every source SHA-256 digest.
4. Build a relative-path VRT mosaic from the two elevation tiles.
5. Rasterize Natural Earth land polygons onto the exact target grid.
6. Run `normalize_atlas.py` for sample-centre registration, previews and provenance.
7. Run `compile-raster-atlas` to create OTAT tiles.
8. Copy normalization metadata into the atlas fixture.
9. Create fixture-wide SHA-256 checksums.
10. Create and verify the deterministic ZIP archive.
11. Compare the archive byte-for-byte with the committed bundle.
12. Extract and sample it through the Java runtime tests.

## Runtime reference points

The Java fixture tests use real locations and expected normalized results:

| Location | Latitude | Longitude | Expected result |
| --- | ---: | ---: | --- |
| Bergen centre | 60.3913 | 5.3221 | land, 15 m nearest elevation |
| Ulriken | 60.3775 | 5.3791 | land, 484 m nearest elevation |
| Fløyen | 60.3988 | 5.3456 | land, 369 m nearest elevation |
| Sotra | 60.35 | 5.05 | land |
| Askøy | 60.45 | 5.15 | land |
| North Sea | 60.45 | 4.8 | water, 0 m nearest elevation |
| Byfjorden | 60.42 | 5.25 | water |
| Fanafjorden | 60.25 | 5.28 | water |

A point at latitude `60.34941176470588` and longitude `5.500980392156863` lies halfway between four
source samples belonging to four different tiles. Its required bilinear elevation is `446.0 m`.

## Expected limitations

- Copernicus DEM is a surface model and may include vegetation and buildings.
- The 256 × 256 fixture is downsampled from GLO-90 and is not the final Scandinavian resolution.
- Natural Earth omits many very small skerries and is not a hydrographic navigation source.
- Inland water is not represented by the Natural Earth land polygon mask used here.
- This fixture proves the pipeline and legal/provenance process; it is not the final production atlas.

## Redistribution boundary

The repository contains only the small adapted OTAT bundle, previews, reports, source locks and required
notices. It does not redistribute the original Copernicus COG tiles or Natural Earth shapefile archive.
Source-code licensing and atlas-data licensing remain separate.
