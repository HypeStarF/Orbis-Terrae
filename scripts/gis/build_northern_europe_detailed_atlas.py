#!/usr/bin/env python3
"""Build a pinned Northern Europe regional atlas from public GIS sources."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROFILE = REPO_ROOT / "atlas/profiles/northern-europe-detailed-v1.json"
NORMALIZER = REPO_ROOT / "scripts/gis/normalize_atlas.py"
ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
NO_DATA = -32768


class Error(RuntimeError):
    """Raised when a regional atlas build contract is invalid."""


@dataclass(frozen=True)
class Bounds:
    west: float
    south: float
    east: float
    north: float


@dataclass(frozen=True)
class Profile:
    path: Path
    dataset_id: str
    atlas_version: str
    compiler_version: str
    bounds: Bounds
    resolution_arc_seconds: int
    width: int
    height: int
    tile_size: int
    source_lock: Path
    output_archive: str
    elevation_resampling: str
    mask_water_to_zero: bool
    land_resampling: str
    land_values: tuple[int, ...]


@dataclass(frozen=True)
class Source:
    source_id: str
    file_name: str
    url: str
    sha256: str | None
    dataset: str
    dataset_version: str
    licence: str
    attribution: str


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def text(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise Error(f"{name} must be a non-empty string")
    return value


def integer(value: Any, name: str, minimum: int = 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise Error(f"{name} must be an integer >= {minimum}")
    return value


def number(value: Any, name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise Error(f"{name} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise Error(f"{name} must be finite")
    return result


def load_profile(path: Path) -> Profile:
    path = path.resolve()
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise Error(f"Invalid atlas profile: {path}") from exception
    if document.get("schemaVersion") != 1:
        raise Error("Unsupported profile schemaVersion")
    bounds_value = document.get("bounds")
    if not isinstance(bounds_value, dict):
        raise Error("bounds must be an object")
    bounds = Bounds(
        number(bounds_value.get("west"), "bounds.west"),
        number(bounds_value.get("south"), "bounds.south"),
        number(bounds_value.get("east"), "bounds.east"),
        number(bounds_value.get("north"), "bounds.north"),
    )
    if not bounds.west < bounds.east or not bounds.south < bounds.north:
        raise Error("Profile bounds must increase west-to-east and south-to-north")
    if bounds.west < -180 or bounds.east > 180 or bounds.south < -90 or bounds.north > 90:
        raise Error("Profile bounds exceed longitude/latitude limits")

    resolution = integer(document.get("resolutionArcSeconds"), "resolutionArcSeconds")
    width = integer(document.get("widthSamples"), "widthSamples", 2)
    height = integer(document.get("heightSamples"), "heightSamples", 2)
    expected_width = round((bounds.east - bounds.west) * 3600 / resolution) + 1
    expected_height = round((bounds.north - bounds.south) * 3600 / resolution) + 1
    if width != expected_width or height != expected_height:
        raise Error(
            "Profile dimensions do not match bounds and resolution: "
            f"expected {expected_width}x{expected_height}, got {width}x{height}"
        )

    elevation = document.get("elevation")
    land_mask = document.get("landMask")
    if not isinstance(elevation, dict) or not isinstance(land_mask, dict):
        raise Error("elevation and landMask must be objects")
    values = land_mask.get("landValues")
    if not isinstance(values, list) or not values:
        raise Error("landMask.landValues must be a non-empty array")
    land_values = tuple(integer(value, "landMask.landValues", 0) for value in values)
    if any(value > 255 for value in land_values) or len(set(land_values)) != len(land_values):
        raise Error("landMask.landValues must be unique byte values")

    source_lock = (path.parent / text(document.get("sourceLock"), "sourceLock")).resolve()
    output_archive = text(document.get("outputArchive"), "outputArchive")
    if Path(output_archive).name != output_archive or not output_archive.lower().endswith(".zip"):
        raise Error("outputArchive must be a simple .zip file name")
    return Profile(
        path=path,
        dataset_id=text(document.get("datasetId"), "datasetId"),
        atlas_version=text(document.get("atlasVersion"), "atlasVersion"),
        compiler_version=text(document.get("compilerVersion"), "compilerVersion"),
        bounds=bounds,
        resolution_arc_seconds=resolution,
        width=width,
        height=height,
        tile_size=integer(document.get("tileSize"), "tileSize", 2),
        source_lock=source_lock,
        output_archive=output_archive,
        elevation_resampling=text(elevation.get("resampling", "bilinear"), "elevation.resampling"),
        mask_water_to_zero=bool(elevation.get("maskWaterToZero", False)),
        land_resampling=text(land_mask.get("resampling", "near"), "landMask.resampling"),
        land_values=land_values,
    )


def load_lock(profile: Profile, path: Path | None = None) -> tuple[dict[str, Any], tuple[Source, ...]]:
    lock_path = (path or profile.source_lock).resolve()
    try:
        document = json.loads(lock_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise Error(f"Invalid source lock: {lock_path}") from exception
    if document.get("schemaVersion") != 1 or document.get("datasetId") != profile.dataset_id:
        raise Error("Source lock schemaVersion or datasetId does not match the profile")
    values = document.get("sources")
    if not isinstance(values, list) or not values:
        raise Error("Source lock must contain at least one source")

    sources: list[Source] = []
    seen_ids: set[str] = set()
    seen_names: set[str] = set()
    for value in values:
        if not isinstance(value, dict):
            raise Error("Source lock entries must be objects")
        source_id = text(value.get("id"), "sources[].id")
        file_name = text(value.get("fileName"), f"{source_id}.fileName")
        if source_id in seen_ids:
            raise Error(f"Duplicate source id: {source_id}")
        if file_name in seen_names or Path(file_name).name != file_name:
            raise Error(f"Duplicate or invalid source fileName: {file_name}")
        seen_ids.add(source_id)
        seen_names.add(file_name)
        url = text(value.get("url"), f"{source_id}.url")
        if not url.startswith("https://"):
            raise Error(f"Source URL must use HTTPS: {url}")
        expected = value.get("sha256")
        if expected is not None:
            expected = text(expected, f"{source_id}.sha256").lower()
            if len(expected) != 64 or any(char not in "0123456789abcdef" for char in expected):
                raise Error(f"Invalid SHA-256 for {source_id}")
        sources.append(
            Source(
                source_id=source_id,
                file_name=file_name,
                url=url,
                sha256=expected,
                dataset=text(value.get("dataset"), f"{source_id}.dataset"),
                dataset_version=text(value.get("datasetVersion"), f"{source_id}.datasetVersion"),
                licence=text(value.get("licence"), f"{source_id}.licence"),
                attribution=text(value.get("attribution"), f"{source_id}.attribution"),
            )
        )

    etopo = tuple(source for source in sources if source.source_id.startswith("etopo-2022-15s-"))
    natural_earth = tuple(source for source in sources if source.source_id == "natural-earth-10m-land")
    if not etopo or len(natural_earth) != 1 or len(etopo) + 1 != len(sources):
        raise Error("Source lock must contain ETOPO tiles and exactly one Natural Earth land archive")
    return document, tuple(sources)


def sample_edge_bounds(profile: Profile) -> tuple[float, float, float, float]:
    x_resolution = (profile.bounds.east - profile.bounds.west) / (profile.width - 1)
    y_resolution = (profile.bounds.north - profile.bounds.south) / (profile.height - 1)
    return (
        profile.bounds.west - x_resolution / 2,
        profile.bounds.south - y_resolution / 2,
        profile.bounds.east + x_resolution / 2,
        profile.bounds.north + y_resolution / 2,
    )


def tile_grid(profile: Profile) -> tuple[int, int]:
    return (
        (profile.width + profile.tile_size - 1) // profile.tile_size,
        (profile.height + profile.tile_size - 1) // profile.tile_size,
    )


def run(arguments: Sequence[str], *, cwd: Path | None = None) -> str:
    try:
        completed = subprocess.run(
            list(arguments),
            cwd=cwd,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except FileNotFoundError as exception:
        raise Error(f"Required executable not found: {arguments[0]}") from exception
    except subprocess.CalledProcessError as exception:
        raise Error(
            f"Command failed: {' '.join(arguments)}\n"
            f"stdout:\n{exception.stdout.strip()}\n"
            f"stderr:\n{exception.stderr.strip()}"
        ) from exception
    if completed.stdout:
        print(completed.stdout, end="")
    if completed.stderr:
        print(completed.stderr, end="", file=sys.stderr)
    return completed.stdout


def download(source: Source, destination: Path, *, require_locked: bool) -> str:
    if require_locked and source.sha256 is None:
        raise Error(f"Source {source.source_id} does not have a pinned SHA-256")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_file():
        actual = sha256(destination)
        if source.sha256 is None or actual == source.sha256:
            return actual
        destination.unlink()
    request = urllib.request.Request(
        source.url,
        headers={"User-Agent": "Orbis-Terrae-regional-atlas-builder/1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            with destination.open("wb") as output:
                shutil.copyfileobj(response, output, length=1024 * 1024)
    except OSError as exception:
        destination.unlink(missing_ok=True)
        raise Error(f"Failed to download {source.url}") from exception
    actual = sha256(destination)
    if source.sha256 is not None and actual != source.sha256:
        destination.unlink(missing_ok=True)
        raise Error(
            f"SHA-256 mismatch for {source.source_id}: expected {source.sha256}, got {actual}"
        )
    return actual


def resolve_lock(profile: Profile, output: Path, cache: Path) -> None:
    document, sources = load_lock(profile)
    resolved_sources: list[dict[str, Any]] = []
    for source, value in zip(sources, document["sources"], strict=True):
        digest = download(source, cache / source.file_name, require_locked=False)
        updated = dict(value)
        updated["sha256"] = digest
        resolved_sources.append(updated)
        print(f"{source.source_id} {digest}")
    resolved = dict(document)
    resolved["sources"] = resolved_sources
    write_json(output.resolve(), resolved)


def extract_zip_safely(archive_path: Path, destination: Path) -> None:
    root = destination.resolve()
    root.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive_path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            target = (root / info.filename).resolve()
            if not target.is_relative_to(root):
                raise Error(f"Archive contains unsafe path: {info.filename}")
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(info) as source, target.open("wb") as output:
                shutil.copyfileobj(source, output)


def format_number(value: float) -> str:
    return format(value, ".15g")


def prepare_sources(
    profile: Profile,
    source_directory: Path,
    sources: tuple[Source, ...],
    actual_hashes: dict[str, str],
) -> tuple[Path, Path]:
    etopo_files = [
        source_directory / source.file_name
        for source in sources
        if source.source_id.startswith("etopo-2022-15s-")
    ]
    list_path = source_directory / "etopo-files.txt"
    list_path.write_text("\n".join(path.name for path in etopo_files) + "\n", encoding="utf-8")
    mosaic = source_directory / "etopo-2022-mosaic.vrt"
    run(
        [
            "gdalbuildvrt",
            "-q",
            "-overwrite",
            "-resolution",
            "highest",
            "-input_file_list",
            list_path.name,
            mosaic.name,
        ],
        cwd=source_directory,
    )

    land_source = next(source for source in sources if source.source_id == "natural-earth-10m-land")
    natural_earth = source_directory / "natural-earth-10m-land"
    extract_zip_safely(source_directory / land_source.file_name, natural_earth)
    shapefile = natural_earth / "ne_10m_land.shp"
    if not shapefile.is_file():
        raise Error("Natural Earth archive did not contain ne_10m_land.shp")

    west, south, east, north = sample_edge_bounds(profile)
    land_mask = source_directory / "natural-earth-land-mask.tif"
    run(
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
            format_number(west),
            format_number(south),
            format_number(east),
            format_number(north),
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

    aligned = source_directory / "etopo-aligned.tif"
    run(
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
            format_number(west),
            format_number(south),
            format_number(east),
            format_number(north),
            "-ts",
            str(profile.width),
            str(profile.height),
            "-r",
            profile.elevation_resampling,
            "-ot",
            "Int16",
            "-dstnodata",
            str(NO_DATA),
            "-ovr",
            "NONE",
            "-et",
            "0",
            "-co",
            "COMPRESS=DEFLATE",
            "-co",
            "TILED=YES",
            str(mosaic),
            str(aligned),
        ]
    )

    elevation = aligned
    if profile.mask_water_to_zero:
        elevation = source_directory / "etopo-land-elevation.tif"
        run(
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
                f"--NoDataValue={NO_DATA}",
                "--type=Int16",
                "--format=GTiff",
                "--co=COMPRESS=DEFLATE",
                "--co=TILED=YES",
                f"--outfile={elevation}",
            ]
        )

    actual_hashes["etopo-2022-mosaic-vrt"] = sha256(mosaic)
    actual_hashes["natural-earth-land-mask-tif"] = sha256(land_mask)
    actual_hashes["aligned-elevation-tif"] = sha256(elevation)
    return elevation, land_mask


def normalization_job(profile: Profile, elevation: Path, land_mask: Path, workspace: Path) -> dict[str, Any]:
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
            "sourceNoData": NO_DATA,
            "resampling": "near",
            "provenance": {
                "sourceId": "noaa-etopo-2022-15s-surface",
                "title": "ETOPO 2022 15 Arc-Second Surface Elevation",
                "datasetVersion": "2022 v1",
                "licence": "CC0-1.0",
                "attribution": "NOAA National Centers for Environmental Information, ETOPO 2022",
                "sourceUrl": "https://www.ncei.noaa.gov/products/etopo-global-relief-model",
                "retrievedDate": "2026-07-26",
                "processing": [
                    "Downloaded pinned NOAA ETOPO 2022 15 arc-second surface GeoTIFF tiles",
                    "Mosaicked source tiles with gdalbuildvrt",
                    "Aligned to inclusive sample-centre bounds with exact output dimensions",
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
                "retrievedDate": "2026-07-26",
                "processing": [
                    "Downloaded the pinned Natural Earth land polygon archive",
                    "Rasterized land polygons onto the exact regional sample-centre grid",
                    "Normalized categorical values with nearest-neighbour resampling",
                ],
            },
        },
    }


def gradle_launcher() -> Path:
    return REPO_ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")


def compile_atlas(normalized: Path, atlas: Path) -> None:
    arguments = " ".join(
        [
            "compile-raster-atlas",
            str(normalized / "atlas-manifest.json"),
            str(normalized / "elevation.raw"),
            str(normalized / "land-mask.raw"),
            str(atlas),
        ]
    )
    run(
        [
            str(gradle_launcher()),
            ":modules:atlas-compiler:run",
            f"--args={arguments}",
            "--no-configuration-cache",
            "--warning-mode=fail",
        ],
        cwd=REPO_ROOT,
    )


def write_fixture_checksums(atlas: Path) -> None:
    checksum_file = atlas / "fixture-checksums.sha256"
    lines = []
    for path in sorted(atlas.rglob("*")):
        if path.is_file() and path != checksum_file:
            lines.append(f"{sha256(path)}  {path.relative_to(atlas).as_posix()}")
    checksum_file.write_text("\n".join(lines) + "\n", encoding="ascii", newline="\n")


def copy_metadata(
    profile: Profile,
    atlas: Path,
    normalized: Path,
    lock_document: dict[str, Any],
    actual_hashes: dict[str, str],
) -> None:
    metadata = atlas / "metadata"
    metadata.mkdir()
    for name in (
        "normalization-report.json",
        "checksums.sha256",
        "elevation-preview.pgm",
        "land-mask-preview.pgm",
    ):
        shutil.copy2(normalized / name, metadata / name)

    resolved = dict(lock_document)
    resolved_sources: list[dict[str, Any]] = []
    for value in lock_document["sources"]:
        updated = dict(value)
        updated["sha256"] = actual_hashes[value["id"]]
        resolved_sources.append(updated)
    resolved["sources"] = resolved_sources
    source_ids = {value["id"] for value in lock_document["sources"]}
    resolved["derivedFiles"] = {
        key: actual_hashes[key] for key in sorted(actual_hashes) if key not in source_ids
    }
    write_json(metadata / "sources.lock.json", resolved)
    write_json(metadata / "release-profile.json", json.loads(profile.path.read_text(encoding="utf-8")))
    (atlas / "ATTRIBUTION.md").write_text(
        "# Northern Europe detailed atlas attribution\n\n"
        "Elevation was produced from NOAA NCEI ETOPO 2022 15 Arc-Second Surface Elevation. "
        "ETOPO 2022 is dedicated to the public domain under CC0-1.0.\n\n"
        "The land mask was made with Natural Earth. Natural Earth data is public domain.\n",
        encoding="utf-8",
        newline="\n",
    )
    write_fixture_checksums(atlas)


def write_archive(atlas: Path, archive_path: Path) -> None:
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(atlas.rglob("*")):
            if not path.is_file():
                continue
            info = zipfile.ZipInfo(path.relative_to(atlas).as_posix(), date_time=ZIP_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = (0o100644 & 0xFFFF) << 16
            info.flag_bits |= 0x800
            archive.writestr(
                info,
                path.read_bytes(),
                compress_type=zipfile.ZIP_DEFLATED,
                compresslevel=9,
            )


def verify_atlas(profile: Profile, atlas: Path) -> None:
    atlas = atlas.resolve()
    checksum_file = atlas / "fixture-checksums.sha256"
    if not checksum_file.is_file():
        raise Error(f"Missing fixture checksum file: {checksum_file}")
    expected_paths: set[str] = set()
    for line in checksum_file.read_text(encoding="ascii").splitlines():
        digest, separator, relative = line.partition("  ")
        if not separator or len(digest) != 64:
            raise Error(f"Invalid fixture checksum line: {line}")
        path = atlas / relative
        if not path.is_file() or sha256(path) != digest:
            raise Error(f"Fixture checksum mismatch: {relative}")
        expected_paths.add(relative)
    actual_paths = {
        path.relative_to(atlas).as_posix()
        for path in atlas.rglob("*")
        if path.is_file() and path != checksum_file
    }
    if actual_paths != expected_paths:
        raise Error("Fixture file set differs from fixture-checksums.sha256")

    manifest = json.loads((atlas / "atlas-manifest.json").read_text(encoding="utf-8"))
    if manifest.get("atlasId") != profile.dataset_id or manifest.get("bounds") != profile.bounds.__dict__:
        raise Error("Atlas manifest identity or bounds do not match the profile")
    layers = {layer["id"]: layer for layer in manifest.get("layers", [])}
    columns, rows = tile_grid(profile)
    for layer_id, directory in (("elevation", "elevation"), ("land_mask", "land-mask")):
        layer = layers.get(layer_id)
        if layer is None:
            raise Error(f"Atlas manifest is missing layer {layer_id}")
        if layer.get("gridWidthSamples") != profile.width or layer.get("gridHeightSamples") != profile.height:
            raise Error(f"Atlas layer dimensions are invalid: {layer_id}")
        if layer.get("tileSize") != profile.tile_size:
            raise Error(f"Atlas layer tile size is invalid: {layer_id}")
        tiles = list((atlas / "layers" / directory / "0").glob("*/*.otat"))
        if len(tiles) != columns * rows:
            raise Error(f"Unexpected {directory} tile count: {len(tiles)}")


def verify(profile: Profile, fixture: Path) -> None:
    fixture = fixture.resolve()
    if fixture.suffix.lower() == ".zip":
        if not fixture.is_file():
            raise Error(f"Missing atlas archive: {fixture}")
        with tempfile.TemporaryDirectory() as directory:
            atlas = Path(directory) / profile.dataset_id
            extract_zip_safely(fixture, atlas)
            verify_atlas(profile, atlas)
    else:
        verify_atlas(profile, fixture)


def build(
    profile: Profile,
    output_root: Path,
    cache: Path,
    lock_path: Path | None,
    *,
    require_locked: bool,
    compare: Path | None,
) -> None:
    output_root = output_root.resolve()
    cache = cache.resolve()
    if output_root.exists():
        raise Error(f"Output already exists: {output_root}")
    lock_document, sources = load_lock(profile, lock_path)
    output_root.mkdir(parents=True)
    try:
        workspace = output_root / "workspace"
        source_directory = workspace / "sources"
        source_directory.mkdir(parents=True)
        actual_hashes: dict[str, str] = {}
        for source in sources:
            cached = cache / source.file_name
            actual_hashes[source.source_id] = download(source, cached, require_locked=require_locked)
            destination = source_directory / source.file_name
            try:
                os.link(cached, destination)
            except OSError:
                shutil.copy2(cached, destination)

        elevation, land_mask = prepare_sources(profile, source_directory, sources, actual_hashes)
        job_path = workspace / "normalization-job.json"
        write_json(job_path, normalization_job(profile, elevation, land_mask, workspace))
        normalized = output_root / "normalized"
        run([sys.executable, str(NORMALIZER), "normalize", str(job_path), str(normalized)])
        atlas = output_root / "atlas"
        compile_atlas(normalized, atlas)
        copy_metadata(profile, atlas, normalized, lock_document, actual_hashes)
        verify_atlas(profile, atlas)
        archive = output_root / profile.output_archive
        write_archive(atlas, archive)
        verify(profile, archive)
        if compare is not None:
            expected = compare.resolve()
            verify(profile, expected)
            if expected.read_bytes() != archive.read_bytes():
                raise Error(
                    "Generated atlas archive differs from expected archive: "
                    f"expected {sha256(expected)}, got {sha256(archive)}"
                )
        print(f"Built {profile.dataset_id} at {atlas}")
        print(f"Created deterministic archive {archive}")
    except BaseException:
        shutil.rmtree(output_root, ignore_errors=True)
        raise


def plan(profile: Profile) -> dict[str, Any]:
    _, sources = load_lock(profile)
    columns, rows = tile_grid(profile)
    sample_count = profile.width * profile.height
    return {
        "datasetId": profile.dataset_id,
        "bounds": profile.bounds.__dict__,
        "resolutionArcSeconds": profile.resolution_arc_seconds,
        "widthSamples": profile.width,
        "heightSamples": profile.height,
        "sampleCountPerLayer": sample_count,
        "tileSize": profile.tile_size,
        "tileColumns": columns,
        "tileRows": rows,
        "tilesPerLayer": columns * rows,
        "elevationRawBytes": sample_count * 2,
        "landMaskRawBytes": sample_count,
        "sourceLock": str(profile.source_lock),
        "sourceCount": len(sources),
        "unlockedSources": [source.source_id for source in sources if source.sha256 is None],
        "outputArchive": profile.output_archive,
        "pixelEdgeBounds": sample_edge_bounds(profile),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, default=DEFAULT_PROFILE)
    parser.add_argument("--lock", type=Path)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("plan")
    resolve_parser = subparsers.add_parser("resolve-lock")
    resolve_parser.add_argument("output_lock", type=Path)
    resolve_parser.add_argument("--cache", type=Path, required=True)
    build_parser = subparsers.add_parser("build")
    build_parser.add_argument("output_root", type=Path)
    build_parser.add_argument("--cache", type=Path, required=True)
    build_parser.add_argument("--allow-unlocked", action="store_true")
    build_parser.add_argument("--compare", type=Path)
    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("fixture", type=Path)
    return parser.parse_args()


def main() -> int:
    arguments = parse_args()
    try:
        profile = load_profile(arguments.profile)
        if arguments.command == "plan":
            print(json.dumps(plan(profile), indent=2))
        elif arguments.command == "resolve-lock":
            resolve_lock(profile, arguments.output_lock, arguments.cache)
        elif arguments.command == "build":
            build(
                profile,
                arguments.output_root,
                arguments.cache,
                arguments.lock,
                require_locked=not arguments.allow_unlocked,
                compare=arguments.compare,
            )
        else:
            verify(profile, arguments.fixture)
            print(f"Valid {profile.dataset_id} fixture {arguments.fixture.resolve()}")
    except Error as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
