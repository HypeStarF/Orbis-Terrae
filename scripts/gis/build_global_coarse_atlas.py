#!/usr/bin/env python3
"""Build the global coarse fallback atlas from pinned public GIS sources."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from typing import Any

REGIONAL_SCRIPT = Path(__file__).with_name("build_northern_europe_detailed_atlas.py")
SPEC = importlib.util.spec_from_file_location("orbis_terrae_elevation_atlas_builder", REGIONAL_SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load the shared elevation atlas builder")
REGIONAL = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = REGIONAL
SPEC.loader.exec_module(REGIONAL)

REPO_ROOT = REGIONAL.REPO_ROOT
DEFAULT_PROFILE = REPO_ROOT / "atlas/profiles/global-coarse-v1.json"
ETOPO_PREFIX = "etopo-2022-60s-"
BASE_COPY_METADATA = REGIONAL.copy_metadata

Error = REGIONAL.Error
load_profile = REGIONAL.load_profile
sample_edge_bounds = REGIONAL.sample_edge_bounds
tile_grid = REGIONAL.tile_grid
plan = REGIONAL.plan


def is_etopo(source_id: str) -> bool:
    return source_id.startswith(ETOPO_PREFIX)


def load_lock(profile: Any, path: Path | None = None) -> tuple[dict[str, Any], tuple[Any, ...]]:
    lock_path = (path or profile.source_lock).resolve()
    try:
        document = REGIONAL.json.loads(lock_path.read_text(encoding="utf-8"))
    except (OSError, REGIONAL.json.JSONDecodeError) as exception:
        raise Error(f"Invalid source lock: {lock_path}") from exception
    if document.get("schemaVersion") != 1 or document.get("datasetId") != profile.dataset_id:
        raise Error("Source lock schemaVersion or datasetId does not match the profile")
    values = document.get("sources")
    if not isinstance(values, list) or not values:
        raise Error("Source lock must contain at least one source")

    sources: list[Any] = []
    seen_ids: set[str] = set()
    seen_names: set[str] = set()
    for value in values:
        if not isinstance(value, dict):
            raise Error("Source lock entries must be objects")
        source_id = REGIONAL.text(value.get("id"), "sources[].id")
        file_name = REGIONAL.text(value.get("fileName"), f"{source_id}.fileName")
        if source_id in seen_ids:
            raise Error(f"Duplicate source id: {source_id}")
        if file_name in seen_names or Path(file_name).name != file_name:
            raise Error(f"Duplicate or invalid source fileName: {file_name}")
        seen_ids.add(source_id)
        seen_names.add(file_name)
        url = REGIONAL.text(value.get("url"), f"{source_id}.url")
        if not url.startswith("https://"):
            raise Error(f"Source URL must use HTTPS: {url}")
        expected = value.get("sha256")
        if expected is not None:
            expected = REGIONAL.text(expected, f"{source_id}.sha256").lower()
            if len(expected) != 64 or any(char not in "0123456789abcdef" for char in expected):
                raise Error(f"Invalid SHA-256 for {source_id}")
        sources.append(
            REGIONAL.Source(
                source_id=source_id,
                file_name=file_name,
                url=url,
                sha256=expected,
                dataset=REGIONAL.text(value.get("dataset"), f"{source_id}.dataset"),
                dataset_version=REGIONAL.text(
                    value.get("datasetVersion"), f"{source_id}.datasetVersion"
                ),
                licence=REGIONAL.text(value.get("licence"), f"{source_id}.licence"),
                attribution=REGIONAL.text(
                    value.get("attribution"), f"{source_id}.attribution"
                ),
            )
        )

    etopo = tuple(source for source in sources if is_etopo(source.source_id))
    natural_earth = tuple(
        source for source in sources if source.source_id == "natural-earth-10m-land"
    )
    if len(etopo) != 1 or len(natural_earth) != 1 or len(sources) != 2:
        raise Error(
            "Global source lock must contain exactly one ETOPO 2022 60s surface raster "
            "and one Natural Earth land archive"
        )
    return document, tuple(sources)


def prepare_sources(
    profile: Any,
    source_directory: Path,
    sources: tuple[Any, ...],
    actual_hashes: dict[str, str],
) -> tuple[Path, Path]:
    etopo_source = next(source for source in sources if is_etopo(source.source_id))
    etopo = source_directory / etopo_source.file_name

    land_source = next(
        source for source in sources if source.source_id == "natural-earth-10m-land"
    )
    natural_earth = source_directory / "natural-earth-10m-land"
    REGIONAL.extract_zip_safely(source_directory / land_source.file_name, natural_earth)
    shapefile = natural_earth / "ne_10m_land.shp"
    if not shapefile.is_file():
        raise Error("Natural Earth archive did not contain ne_10m_land.shp")

    west, south, east, north = sample_edge_bounds(profile)
    land_mask = source_directory / "natural-earth-global-land-mask.tif"
    REGIONAL.run(
        [
            "gdal_rasterize",
            "-q",
            "-burn",
            "1",
            "-init",
            "0",
            "-a_nodata",
            "0",
            "-a_srs",
            "EPSG:4326",
            "-ot",
            "Byte",
            "-of",
            "GTiff",
            "-te",
            REGIONAL.format_number(west),
            REGIONAL.format_number(south),
            REGIONAL.format_number(east),
            REGIONAL.format_number(north),
            "-ts",
            str(profile.width),
            str(profile.height),
            "-co",
            "COMPRESS=DEFLATE",
            "-co",
            "TILED=YES",
            str(shapefile),
            str(land_mask),
        ]
    )

    aligned = source_directory / "etopo-global-aligned.tif"
    REGIONAL.run(
        [
            "gdalwarp",
            "-q",
            "-overwrite",
            "-of",
            "GTiff",
            "-t_srs",
            "EPSG:4326",
            "-te_srs",
            "EPSG:4326",
            "-te",
            REGIONAL.format_number(west),
            REGIONAL.format_number(south),
            REGIONAL.format_number(east),
            REGIONAL.format_number(north),
            "-ts",
            str(profile.width),
            str(profile.height),
            "-r",
            profile.elevation_resampling,
            "-ot",
            "Int16",
            "-dstnodata",
            str(REGIONAL.NO_DATA),
            "-ovr",
            "NONE",
            "-et",
            "0",
            "-co",
            "COMPRESS=DEFLATE",
            "-co",
            "TILED=YES",
            str(etopo),
            str(aligned),
        ]
    )

    elevation = aligned
    if profile.mask_water_to_zero:
        elevation = source_directory / "etopo-global-land-elevation.tif"
        REGIONAL.run(
            [
                "gdal_calc.py",
                "--quiet",
                "--overwrite",
                "--projectionCheck",
                "-A",
                str(aligned),
                "-B",
                str(land_mask),
                "--calc=where(B==1,A,0)",
                f"--NoDataValue={REGIONAL.NO_DATA}",
                "--type=Int16",
                "--format=GTiff",
                "--co=COMPRESS=DEFLATE",
                "--co=TILED=YES",
                f"--outfile={elevation}",
            ]
        )

    actual_hashes["natural-earth-global-land-mask-tif"] = REGIONAL.sha256(land_mask)
    actual_hashes["aligned-global-elevation-tif"] = REGIONAL.sha256(elevation)
    return elevation, land_mask


def normalization_job(
    profile: Any, elevation: Path, land_mask: Path, workspace: Path
) -> dict[str, Any]:
    target_arc_minutes = profile.resolution_arc_seconds / 60
    return {
        "schemaVersion": 1,
        "atlas": {
            "id": profile.dataset_id,
            "version": profile.atlas_version,
            "compilerVersion": profile.compiler_version,
            "bounds": profile.bounds.__dict__,
            "tileSize": profile.tile_size,
        },
        "elevation": {
            "source": elevation.relative_to(workspace).as_posix(),
            "widthSamples": profile.width,
            "heightSamples": profile.height,
            "sourceNoData": REGIONAL.NO_DATA,
            "resampling": "near",
            "provenance": {
                "sourceId": "noaa-etopo-2022-60s-surface",
                "title": "ETOPO 2022 60 Arc-Second Surface Elevation",
                "datasetVersion": "2022 v1",
                "licence": "CC0-1.0",
                "attribution": "NOAA National Centers for Environmental Information, ETOPO 2022",
                "sourceUrl": "https://www.ncei.noaa.gov/products/etopo-global-relief-model",
                "retrievedDate": "2026-07-27",
                "processing": [
                    "Downloaded the pinned global ETOPO 2022 60 arc-second surface GeoTIFF",
                    (
                        "Resampled to "
                        f"{REGIONAL.format_number(target_arc_minutes)} arc-minute cell-centred samples"
                    ),
                    "Aligned pixel edges exactly to 180°W–180°E and 90°S–90°N",
                    "Applied the Natural Earth land mask so ocean elevation samples are zero",
                ],
            },
        },
        "landMask": {
            "source": land_mask.relative_to(workspace).as_posix(),
            "widthSamples": profile.width,
            "heightSamples": profile.height,
            "sourceNoData": 0,
            "resampling": profile.land_resampling,
            "landValues": list(profile.land_values),
            "provenance": {
                "sourceId": "natural-earth-10m-land-5_1_1",
                "title": "Natural Earth 1:10m Land",
                "datasetVersion": "5.1.1",
                "licence": "Public domain",
                "attribution": "Made with Natural Earth",
                "sourceUrl": "https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-land/",
                "retrievedDate": "2026-07-27",
                "processing": [
                    "Downloaded the pinned Natural Earth land polygon archive",
                    "Rasterized land polygons onto the exact global cell-centred grid",
                    "Normalized categorical values with nearest-neighbour resampling",
                ],
            },
        },
    }


def copy_metadata(
    profile: Any,
    atlas: Path,
    normalized: Path,
    lock_document: dict[str, Any],
    actual_hashes: dict[str, str],
) -> None:
    BASE_COPY_METADATA(profile, atlas, normalized, lock_document, actual_hashes)
    (atlas / "ATTRIBUTION.md").write_text(
        "# Global coarse atlas attribution\n\n"
        "Elevation was produced from NOAA NCEI ETOPO 2022 60 Arc-Second Surface Elevation. "
        "ETOPO 2022 is dedicated to the public domain under CC0-1.0.\n\n"
        "The land mask was made with Natural Earth. Natural Earth data is public domain.\n",
        encoding="utf-8",
        newline="\n",
    )
    REGIONAL.write_fixture_checksums(atlas)


def install_global_overrides() -> None:
    REGIONAL.DEFAULT_PROFILE = DEFAULT_PROFILE
    REGIONAL.load_lock = load_lock
    REGIONAL.prepare_sources = prepare_sources
    REGIONAL.normalization_job = normalization_job
    REGIONAL.copy_metadata = copy_metadata


def main() -> int:
    install_global_overrides()
    return REGIONAL.main()


install_global_overrides()

if __name__ == "__main__":
    raise SystemExit(main())
