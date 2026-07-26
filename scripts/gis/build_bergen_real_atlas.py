#!/usr/bin/env python3
"""Build the pinned Bergen real-data atlas fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
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

DATASET_ID = "bergen-real-v1"
WIDTH = 256
HEIGHT = 256
TILE_SIZE = 64
BOUNDS = {
    "west": 4.75,
    "south": 60.2,
    "east": 5.75,
    "north": 60.8,
}
REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LOCK = REPO_ROOT / "atlas/datasets/bergen-real-v1/sources.lock.json"
NORMALIZER = REPO_ROOT / "scripts/gis/normalize_atlas.py"


class Error(RuntimeError):
    """Raised when the fixture cannot be built or verified."""


@dataclass(frozen=True)
class Source:
    source_id: str
    file_name: str
    url: str
    sha256: str | None


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
        command = " ".join(arguments)
        raise Error(
            f"Command failed: {command}\n"
            f"stdout:\n{exception.stdout.strip()}\n"
            f"stderr:\n{exception.stderr.strip()}"
        ) from exception
    if completed.stdout:
        print(completed.stdout, end="")
    if completed.stderr:
        print(completed.stderr, end="", file=sys.stderr)
    return completed.stdout


def load_lock(path: Path) -> tuple[dict[str, Any], tuple[Source, ...]]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise Error(f"Invalid source lock: {path}") from exception
    if document.get("schemaVersion") != 1 or document.get("datasetId") != DATASET_ID:
        raise Error("Source lock schemaVersion or datasetId is invalid")
    if document.get("bounds") != BOUNDS:
        raise Error("Source lock bounds do not match the fixture contract")
    if document.get("widthSamples") != WIDTH or document.get("heightSamples") != HEIGHT:
        raise Error("Source lock raster dimensions do not match the fixture contract")
    sources_value = document.get("sources")
    if not isinstance(sources_value, list) or len(sources_value) != 3:
        raise Error("Source lock must contain exactly three sources")
    sources: list[Source] = []
    seen: set[str] = set()
    for value in sources_value:
        if not isinstance(value, dict):
            raise Error("Source lock entries must be objects")
        source_id = require_text(value.get("id"), "sources[].id")
        if source_id in seen:
            raise Error(f"Duplicate source id: {source_id}")
        seen.add(source_id)
        file_name = require_text(value.get("fileName"), f"{source_id}.fileName")
        if Path(file_name).name != file_name:
            raise Error(f"Source fileName must be a simple file name: {file_name}")
        url = require_text(value.get("url"), f"{source_id}.url")
        if not url.startswith("https://"):
            raise Error(f"Source URL must use HTTPS: {url}")
        expected = value.get("sha256")
        if expected is not None:
            expected = require_text(expected, f"{source_id}.sha256").lower()
            if len(expected) != 64 or any(char not in "0123456789abcdef" for char in expected):
                raise Error(f"Invalid SHA-256 for {source_id}")
        sources.append(Source(source_id, file_name, url, expected))
    expected_ids = {
        "copernicus-dem-glo90-n60-e004",
        "copernicus-dem-glo90-n60-e005",
        "natural-earth-10m-land",
    }
    if seen != expected_ids:
        raise Error(f"Unexpected source ids: {sorted(seen)}")
    return document, tuple(sources)


def require_text(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise Error(f"{name} must be a non-empty string")
    return value


def download(source: Source, destination: Path, *, require_locked: bool) -> str:
    if require_locked and source.sha256 is None:
        raise Error(f"Source {source.source_id} does not have a pinned SHA-256")
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        source.url,
        headers={"User-Agent": "Orbis-Terrae-atlas-builder/1"},
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            with destination.open("wb") as output:
                shutil.copyfileobj(response, output, length=1024 * 1024)
    except OSError as exception:
        raise Error(f"Failed to download {source.url}") from exception
    actual = sha256(destination)
    if source.sha256 is not None and actual != source.sha256:
        raise Error(
            f"SHA-256 mismatch for {source.source_id}: expected {source.sha256}, got {actual}"
        )
    return actual


def sample_edge_bounds() -> tuple[float, float, float, float]:
    x_resolution = (BOUNDS["east"] - BOUNDS["west"]) / (WIDTH - 1)
    y_resolution = (BOUNDS["north"] - BOUNDS["south"]) / (HEIGHT - 1)
    return (
        BOUNDS["west"] - x_resolution / 2,
        BOUNDS["south"] - y_resolution / 2,
        BOUNDS["east"] + x_resolution / 2,
        BOUNDS["north"] + y_resolution / 2,
    )


def build_sources(
    source_directory: Path,
    sources: tuple[Source, ...],
    actual_hashes: dict[str, str],
) -> tuple[Path, Path]:
    copernicus = [
        source_directory / source.file_name
        for source in sources
        if source.source_id.startswith("copernicus-dem")
    ]
    natural_earth = next(
        source_directory / source.file_name
        for source in sources
        if source.source_id == "natural-earth-10m-land"
    )
    dem_vrt = source_directory / "copernicus-dem-mosaic.vrt"
    run(
        [
            "gdalbuildvrt",
            "-q",
            "-resolution",
            "highest",
            dem_vrt.name,
            *(path.name for path in copernicus),
        ],
        cwd=source_directory,
    )
    natural_earth_directory = source_directory / "natural-earth-10m-land"
    with zipfile.ZipFile(natural_earth) as archive:
        archive.extractall(natural_earth_directory)
    shapefile = natural_earth_directory / "ne_10m_land.shp"
    if not shapefile.is_file():
        raise Error("Natural Earth archive did not contain ne_10m_land.shp")
    land_mask = source_directory / "natural-earth-land-mask.tif"
    west, south, east, north = sample_edge_bounds()
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
            str(WIDTH),
            str(HEIGHT),
            "-co",
            "COMPRESS=DEFLATE",
            "-co",
            "TILED=YES",
            str(shapefile),
            str(land_mask),
        ]
    )
    if not dem_vrt.is_file() or not land_mask.is_file():
        raise Error("Source preparation did not create both source rasters")
    actual_hashes["copernicus-dem-mosaic-vrt"] = sha256(dem_vrt)
    actual_hashes["natural-earth-land-mask-tif"] = sha256(land_mask)
    return dem_vrt, land_mask


def normalization_job(dem_vrt: Path, land_mask: Path, workspace: Path) -> dict[str, Any]:
    relative_dem = dem_vrt.relative_to(workspace).as_posix()
    relative_land = land_mask.relative_to(workspace).as_posix()
    return {
        "schemaVersion": 1,
        "atlas": {
            "id": DATASET_ID,
            "version": "1.0.0",
            "compilerVersion": "0.1.0-SNAPSHOT",
            "bounds": BOUNDS,
            "tileSize": TILE_SIZE,
        },
        "elevation": {
            "source": relative_dem,
            "widthSamples": WIDTH,
            "heightSamples": HEIGHT,
            "sourceNoData": None,
            "resampling": "bilinear",
            "provenance": {
                "sourceId": "copernicus-dem-glo90-2021",
                "title": "Copernicus DEM GLO-90",
                "datasetVersion": "2021 release, AWS COG conversion",
                "licence": "Copernicus WorldDEM-90 free and open licence",
                "attribution": (
                    "produced using Copernicus WorldDEM-90 © DLR e.V. 2010-2014 and "
                    "© Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS "
                    "by the European Union and ESA; all rights reserved"
                ),
                "sourceUrl": "https://registry.opendata.aws/copernicus-dem/",
                "retrievedDate": "2026-07-26",
                "processing": [
                    "Downloaded public GLO-90 COG tiles N60E004 and N60E005 from AWS",
                    "Mosaicked source COGs with gdalbuildvrt",
                    "Normalized to the Bergen sample-centre grid with bilinear resampling",
                ],
            },
        },
        "landMask": {
            "source": relative_land,
            "widthSamples": WIDTH,
            "heightSamples": HEIGHT,
            "sourceNoData": 0,
            "resampling": "near",
            "landValues": [1],
            "provenance": {
                "sourceId": "natural-earth-10m-land-5_1_1",
                "title": "Natural Earth 1:10m Land",
                "datasetVersion": "5.1.1",
                "licence": "Public domain",
                "attribution": "Made with Natural Earth",
                "sourceUrl": "https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-land/",
                "retrievedDate": "2026-07-26",
                "processing": [
                    "Downloaded the Natural Earth 1:10m land polygon archive",
                    "Rasterized land polygons onto the exact Bergen sample-centre grid",
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


def copy_metadata(
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
    resolved_lock = dict(lock_document)
    resolved_sources: list[dict[str, Any]] = []
    for value in lock_document["sources"]:
        updated = dict(value)
        updated["sha256"] = actual_hashes[value["id"]]
        resolved_sources.append(updated)
    resolved_lock["sources"] = resolved_sources
    resolved_lock["derivedFiles"] = {
        key: actual_hashes[key]
        for key in sorted(actual_hashes)
        if key not in {value["id"] for value in lock_document["sources"]}
    }
    write_json(metadata / "sources.lock.json", resolved_lock)
    (atlas / "ATTRIBUTION.md").write_text(
        "# Bergen real-data atlas attribution\n\n"
        "Elevation was produced using Copernicus WorldDEM-90 © DLR e.V. 2010-2014 "
        "and © Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by "
        "the European Union and ESA; all rights reserved.\n\n"
        "The land mask was made with Natural Earth. Natural Earth data is public domain.\n",
        encoding="utf-8",
        newline="\n",
    )
    write_fixture_checksums(atlas)


def write_fixture_checksums(atlas: Path) -> None:
    checksum_file = atlas / "fixture-checksums.sha256"
    lines = []
    for path in sorted(atlas.rglob("*")):
        if path.is_file() and path != checksum_file:
            lines.append(f"{sha256(path)}  {path.relative_to(atlas).as_posix()}")
    checksum_file.write_text("\n".join(lines) + "\n", encoding="ascii", newline="\n")


def verify_fixture(atlas: Path) -> None:
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
        if not path.is_file():
            raise Error(f"Missing fixture file: {relative}")
        if sha256(path) != digest:
            raise Error(f"Fixture checksum mismatch: {relative}")
        expected_paths.add(relative)
    actual_paths = {
        path.relative_to(atlas).as_posix()
        for path in atlas.rglob("*")
        if path.is_file() and path != checksum_file
    }
    if actual_paths != expected_paths:
        raise Error(
            f"Fixture file set differs: missing={sorted(expected_paths - actual_paths)}, "
            f"unexpected={sorted(actual_paths - expected_paths)}"
        )
    manifest = json.loads((atlas / "atlas-manifest.json").read_text(encoding="utf-8"))
    if manifest.get("atlasId") != DATASET_ID or manifest.get("bounds") != BOUNDS:
        raise Error("Fixture manifest identity or bounds are invalid")
    layers = {layer["id"]: layer for layer in manifest.get("layers", [])}
    for layer_id in ("elevation", "land_mask"):
        layer = layers.get(layer_id)
        if layer is None:
            raise Error(f"Fixture manifest is missing layer {layer_id}")
        if layer.get("gridWidthSamples") != WIDTH or layer.get("gridHeightSamples") != HEIGHT:
            raise Error(f"Fixture layer dimensions are invalid: {layer_id}")
        if layer.get("tileSize") != TILE_SIZE:
            raise Error(f"Fixture tile size is invalid: {layer_id}")
    expected_tiles = (WIDTH + TILE_SIZE - 1) // TILE_SIZE
    for layer_id in ("elevation", "land-mask"):
        tile_root = atlas / "layers" / layer_id / "0"
        tiles = list(tile_root.glob("*/*.otat"))
        if len(tiles) != expected_tiles * expected_tiles:
            raise Error(f"Unexpected {layer_id} tile count: {len(tiles)}")


def compare_directories(expected: Path, actual: Path) -> None:
    verify_fixture(expected)
    verify_fixture(actual)
    expected_files = {
        path.relative_to(expected).as_posix(): sha256(path)
        for path in expected.rglob("*")
        if path.is_file()
    }
    actual_files = {
        path.relative_to(actual).as_posix(): sha256(path)
        for path in actual.rglob("*")
        if path.is_file()
    }
    if expected_files != actual_files:
        changed = sorted(
            path
            for path in expected_files.keys() | actual_files.keys()
            if expected_files.get(path) != actual_files.get(path)
        )
        raise Error(f"Generated fixture differs from checked-in fixture: {changed}")


def build(
    output_root: Path,
    lock_path: Path,
    *,
    require_locked: bool,
    compare: Path | None,
) -> None:
    output_root = output_root.resolve()
    if output_root.exists():
        raise Error(f"Output already exists: {output_root}")
    lock_document, sources = load_lock(lock_path.resolve())
    output_root.mkdir(parents=True)
    try:
        workspace = output_root / "workspace"
        source_directory = workspace / "sources"
        source_directory.mkdir(parents=True)
        actual_hashes: dict[str, str] = {}
        for source in sources:
            path = source_directory / source.file_name
            actual_hashes[source.source_id] = download(
                source,
                path,
                require_locked=require_locked,
            )
        dem_vrt, land_mask = build_sources(source_directory, sources, actual_hashes)
        job_path = workspace / "normalization-job.json"
        write_json(job_path, normalization_job(dem_vrt, land_mask, workspace))
        normalized = output_root / "normalized"
        run([sys.executable, str(NORMALIZER), "normalize", str(job_path), str(normalized)])
        atlas = output_root / "atlas"
        compile_atlas(normalized, atlas)
        copy_metadata(atlas, normalized, lock_document, actual_hashes)
        verify_fixture(atlas)
        if compare is not None:
            compare_directories(compare.resolve(), atlas)
        print(f"Built {DATASET_ID} at {atlas}")
    except BaseException:
        shutil.rmtree(output_root, ignore_errors=True)
        raise


def format_number(value: float) -> str:
    return format(value, ".15g")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("plan")
    build_parser = subparsers.add_parser("build")
    build_parser.add_argument("output_root", type=Path)
    build_parser.add_argument("--require-locked", action="store_true")
    build_parser.add_argument("--compare", type=Path)
    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("atlas", type=Path)
    return parser.parse_args()


def main() -> int:
    arguments = parse_args()
    try:
        if arguments.command == "plan":
            document, sources = load_lock(arguments.lock.resolve())
            print(
                json.dumps(
                    {
                        "datasetId": DATASET_ID,
                        "bounds": BOUNDS,
                        "widthSamples": WIDTH,
                        "heightSamples": HEIGHT,
                        "tileSize": TILE_SIZE,
                        "pixelEdgeBounds": sample_edge_bounds(),
                        "sources": [source.__dict__ for source in sources],
                        "lock": document,
                    },
                    indent=2,
                )
            )
        elif arguments.command == "build":
            build(
                arguments.output_root,
                arguments.lock,
                require_locked=arguments.require_locked,
                compare=arguments.compare,
            )
        else:
            verify_fixture(arguments.atlas)
            print(f"Valid {DATASET_ID} fixture {arguments.atlas.resolve()}")
    except Error as exception:
        print(f"error: {exception}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
