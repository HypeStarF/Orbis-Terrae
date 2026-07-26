#!/usr/bin/env python3
"""Normalize GIS rasters into Orbis Terrae compiler inputs."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

MIN_GDAL = (3, 8, 0)
NO_DATA = -32768
FILES = (
    "atlas-manifest.json",
    "normalization-report.json",
    "elevation.raw",
    "land-mask.raw",
    "elevation-preview.pgm",
    "land-mask-preview.pgm",
)


class Error(RuntimeError):
    pass


@dataclass(frozen=True)
class Bounds:
    west: float
    south: float
    east: float
    north: float


@dataclass(frozen=True)
class Layer:
    source_text: str
    source: Path
    width: int
    height: int
    source_no_data: float | None
    resampling: str
    provenance: dict[str, Any]
    land_values: tuple[int, ...] = ()


@dataclass(frozen=True)
class Job:
    path: Path
    sha256: str
    atlas: dict[str, Any]
    bounds: Bounds
    tile_size: int
    elevation: Layer
    land_mask: Layer


def load_job(path: Path) -> Job:
    path = path.resolve()
    raw = path.read_bytes()
    try:
        doc = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise Error(f"Invalid UTF-8 JSON job: {path}") from exc
    if doc.get("schemaVersion") != 1:
        raise Error("Unsupported normalization schemaVersion")
    atlas = require_dict(doc.get("atlas"), "atlas")
    bounds_doc = require_dict(atlas.get("bounds"), "atlas.bounds")
    bounds = Bounds(*(number(bounds_doc.get(k), f"atlas.bounds.{k}") for k in
                      ("west", "south", "east", "north")))
    if not bounds.west < bounds.east or not bounds.south < bounds.north:
        raise Error("Atlas bounds must increase west-to-east and south-to-north")
    if bounds.west < -180 or bounds.east > 180 or bounds.south < -90 or bounds.north > 90:
        raise Error("Atlas bounds exceed longitude/latitude limits")
    root = path.parent
    elevation = load_layer(doc.get("elevation"), "elevation", root, "bilinear", False)
    land_mask = load_layer(doc.get("landMask"), "landMask", root, "near", True)
    if elevation.provenance["sourceId"] == land_mask.provenance["sourceId"]:
        raise Error("Layer provenance sourceId values must be unique")
    return Job(
        path=path,
        sha256=hashlib.sha256(raw).hexdigest(),
        atlas=atlas,
        bounds=bounds,
        tile_size=integer(atlas.get("tileSize"), "atlas.tileSize", 2, 4096),
        elevation=elevation,
        land_mask=land_mask,
    )


def load_layer(value: Any, name: str, root: Path, default_resampling: str, land: bool) -> Layer:
    doc = require_dict(value, name)
    source_text = text(doc.get("source"), f"{name}.source")
    resampling = text(doc.get("resampling", default_resampling), f"{name}.resampling")
    allowed = {"near", "bilinear", "cubic", "cubicspline", "lanczos", "average", "mode"}
    if resampling not in allowed:
        raise Error(f"{name}.resampling must be one of {sorted(allowed)}")
    source_no_data = doc.get("sourceNoData")
    if source_no_data is not None:
        source_no_data = number(source_no_data, f"{name}.sourceNoData")
    land_values: tuple[int, ...] = ()
    if land:
        values = doc.get("landValues")
        if not isinstance(values, list) or not values:
            raise Error("landMask.landValues must be a non-empty array")
        land_values = tuple(integer(v, "landMask.landValues", 0, 255) for v in values)
        if len(set(land_values)) != len(land_values):
            raise Error("landMask.landValues must be unique")
    provenance = require_dict(doc.get("provenance"), f"{name}.provenance")
    required = ("sourceId", "title", "datasetVersion", "licence", "attribution",
                "sourceUrl", "retrievedDate", "processing")
    for key in required:
        if key == "processing":
            if not isinstance(provenance.get(key), list) or not provenance[key]:
                raise Error(f"{name}.provenance.processing must be non-empty")
        else:
            text(provenance.get(key), f"{name}.provenance.{key}")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", provenance["sourceId"]):
        raise Error(f"{name}.provenance.sourceId is invalid")
    return Layer(
        source_text=source_text,
        source=(root / source_text).resolve(),
        width=integer(doc.get("widthSamples"), f"{name}.widthSamples", 2),
        height=integer(doc.get("heightSamples"), f"{name}.heightSamples", 2),
        source_no_data=source_no_data,
        resampling=resampling,
        provenance=provenance,
        land_values=land_values,
    )


def geometry(bounds: Bounds, width: int, height: int) -> dict[str, Any]:
    xres = (bounds.east - bounds.west) / (width - 1)
    yres = (bounds.north - bounds.south) / (height - 1)
    return {
        "registration": "sample_centres_inclusive",
        "widthSamples": width,
        "heightSamples": height,
        "xResolutionDegrees": xres,
        "yResolutionDegrees": yres,
        "pixelEdgeBounds": {
            "west": bounds.west - xres / 2,
            "south": bounds.south - yres / 2,
            "east": bounds.east + xres / 2,
            "north": bounds.north + yres / 2,
        },
    }


def run(arguments: Sequence[str]) -> str:
    env = os.environ.copy()
    env.update({"PROJ_NETWORK": "OFF", "GDAL_NUM_THREADS": "1", "CPL_DEBUG": "OFF"})
    try:
        done = subprocess.run(arguments, check=True, capture_output=True, text=True,
                              encoding="utf-8", errors="replace", env=env)
    except FileNotFoundError as exc:
        raise Error(f"Required executable not found: {arguments[0]}") from exc
    except subprocess.CalledProcessError as exc:
        raise Error(f"Command failed: {' '.join(arguments)}\n{exc.stderr.strip()}") from exc
    return done.stdout


def tool(name: str, gdal_bin: Path | None) -> str:
    executable = name + (".exe" if os.name == "nt" else "")
    return str(gdal_bin / executable) if gdal_bin else executable


def normalize(job: Job, output: Path, gdal_bin: Path | None, keep_intermediate: bool) -> None:
    output = output.resolve()
    if output.exists():
        raise Error(f"Output already exists: {output}")
    for name, layer in (("elevation", job.elevation), ("landMask", job.land_mask)):
        if not layer.source.is_file():
            raise Error(f"{name}.source is not a regular file: {layer.source}")
    output.mkdir(parents=True)
    try:
        version = run([tool("gdalinfo", gdal_bin), "--version"]).strip()
        match = re.search(r"GDAL\s+(\d+)\.(\d+)\.(\d+)", version)
        if not match or tuple(map(int, match.groups())) < MIN_GDAL:
            raise Error(f"GDAL {'.'.join(map(str, MIN_GDAL))} or newer is required")
        workspace = output / "intermediate" if keep_intermediate else Path(
            tempfile.mkdtemp(prefix=".gdal-", dir=output)
        )
        if keep_intermediate:
            workspace.mkdir()
        elevation = normalize_layer(job, job.elevation, "elevation", workspace, output,
                                    "Int16", NO_DATA, gdal_bin)
        land_mask = normalize_layer(job, job.land_mask, "land_mask", workspace, output,
                                    "Byte", 0, gdal_bin)
        if not keep_intermediate:
            shutil.rmtree(workspace)
        write_json(output / "atlas-manifest.json", manifest(job))
        write_json(output / "normalization-report.json",
                   report(job, match.group(0), elevation, land_mask))
        write_checksums(output)
        verify(output)
    except BaseException:
        shutil.rmtree(output, ignore_errors=True)
        raise


def normalize_layer(job: Job, layer: Layer, layer_id: str, workspace: Path, output: Path,
                    output_type: str, dst_no_data: int, gdal_bin: Path | None) -> dict[str, Any]:
    grid = geometry(job.bounds, layer.width, layer.height)
    edges = grid["pixelEdgeBounds"]
    warped = workspace / f"{layer_id}-warped.tif"
    command = [
        tool("gdalwarp", gdal_bin), "-q", "-overwrite", "-of", "GTiff",
        "-t_srs", "EPSG:4326", "-te_srs", "EPSG:4326", "-te",
        fmt(edges["west"]), fmt(edges["south"]), fmt(edges["east"]), fmt(edges["north"]),
        "-ts", str(layer.width), str(layer.height), "-r", layer.resampling,
        "-ot", output_type, "-dstnodata", str(dst_no_data), "-ovr", "NONE", "-et", "0",
        "-co", "COMPRESS=NONE", "-co", "TILED=NO", "-co", "BIGTIFF=IF_NEEDED",
    ]
    if layer.source_no_data is not None:
        command += ["-srcnodata", fmt(layer.source_no_data)]
    command += [str(layer.source), str(warped)]
    run(command)
    info = json.loads(run([tool("gdalinfo", gdal_bin), "-json", "-checksum", str(warped)]))
    envi = workspace / f"{layer_id}.bin"
    run([tool("gdal_translate", gdal_bin), "-q", "-strict", "-of", "ENVI",
         "-ot", output_type, "-co", "INTERLEAVE=BSQ", str(warped), str(envi)])
    raw_name = "elevation.raw" if layer_id == "elevation" else "land-mask.raw"
    raw = output / raw_name
    header = parse_envi(envi.with_suffix(".hdr"), layer.width, layer.height,
                        2 if layer_id == "elevation" else 1)
    payload = read_payload(envi, header, layer.width * layer.height * (2 if layer_id == "elevation" else 1))
    if layer_id == "elevation":
        if header["byte order"] == 1:
            payload = b"".join(payload[i:i + 2][::-1] for i in range(0, len(payload), 2))
        samples = tuple(v[0] for v in struct.iter_unpack("<h", payload))
        stats = elevation_stats(samples)
        preview = "elevation-preview.pgm"
        write_elevation_preview(output / preview, layer.width, layer.height, samples, stats)
    else:
        payload = bytes(1 if value in set(layer.land_values) else 0 for value in payload)
        stats = {"sampleCount": len(payload), "landSampleCount": sum(payload),
                 "waterSampleCount": len(payload) - sum(payload)}
        preview = "land-mask-preview.pgm"
        write_pgm(output / preview, layer.width, layer.height,
                  bytes(255 if value else 0 for value in payload), "land mask")
    raw.write_bytes(payload)
    band = info.get("bands", [{}])[0]
    return {
        "id": layer_id,
        "source": {"path": layer.source_text, "sha256": sha256(layer.source),
                   "sizeBytes": layer.source.stat().st_size},
        "grid": grid,
        "resampling": layer.resampling,
        "warpCommand": [
            "<gdalwarp>",
            *("<source>" if arg == str(layer.source) else
              "<warped-intermediate>" if arg == str(warped) else arg
              for arg in command[1:])
        ],
        "gdalInfo": {
            "driver": info.get("driverShortName"),
            "size": info.get("size"),
            "geoTransform": info.get("geoTransform"),
            "band": {"type": band.get("type"), "noDataValue": band.get("noDataValue"),
                     "checksum": band.get("checksum")},
        },
        "normalized": {"path": raw_name, "sha256": sha256(raw),
                       "sizeBytes": raw.stat().st_size, "statistics": stats},
        "preview": {"path": preview, "sha256": sha256(output / preview), "format": "PGM P5"},
    }


def parse_envi(path: Path, width: int, height: int, data_type: int) -> dict[str, int]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="ascii").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key.strip().lower()] = value.strip().strip("{} ")
    required = {"samples": width, "lines": height, "bands": 1, "data type": data_type}
    parsed = {key: int(values.get(key, "-1")) for key in (*required, "header offset", "byte order")}
    for key, expected in required.items():
        if parsed[key] != expected:
            raise Error(f"Unexpected ENVI {key}: {parsed[key]}; expected {expected}")
    if values.get("interleave", "").lower() != "bsq" or parsed["byte order"] not in (0, 1):
        raise Error("ENVI export must be single-band BSQ with byte order 0 or 1")
    return parsed


def read_payload(path: Path, header: dict[str, int], size: int) -> bytes:
    raw = path.read_bytes()
    payload = raw[header["header offset"]:]
    if len(payload) != size:
        raise Error(f"ENVI payload size {len(payload)} does not match expected {size}")
    return payload


def manifest(job: Job) -> dict[str, Any]:
    def provenance(layer: Layer, operations: list[str]) -> dict[str, Any]:
        value = dict(layer.provenance)
        value["processing"] = [*value["processing"], *operations]
        return value
    atlas = job.atlas
    return {
        "schemaVersion": 1,
        "atlasId": text(atlas.get("id"), "atlas.id"),
        "atlasVersion": text(atlas.get("version"), "atlas.version"),
        "compilerVersion": text(atlas.get("compilerVersion"), "atlas.compilerVersion"),
        "projection": "equirectangular",
        "bounds": job.bounds.__dict__,
        "layers": [
            {"id": "elevation", "type": "elevation", "formatVersion": 1,
             "encoding": "signed_int16_le", "tileSize": job.tile_size, "zoom": 0,
             "gridWidthSamples": job.elevation.width,
             "gridHeightSamples": job.elevation.height, "noDataValue": NO_DATA,
             "pathTemplate": "layers/elevation/{z}/{x}/{y}.otat"},
            {"id": "land_mask", "type": "land_mask", "formatVersion": 1,
             "encoding": "packed_bitset_lsb0", "tileSize": job.tile_size, "zoom": 0,
             "gridWidthSamples": job.land_mask.width,
             "gridHeightSamples": job.land_mask.height,
             "pathTemplate": "layers/land-mask/{z}/{x}/{y}.otat"},
        ],
        "provenance": [
            provenance(job.elevation, ["Reprojected to EPSG:4326",
                "Registered sample centres to inclusive atlas bounds",
                f"Resampled to {job.elevation.width}x{job.elevation.height} using {job.elevation.resampling}",
                f"Encoded signed int16 little-endian with no-data {NO_DATA}"]),
            provenance(job.land_mask, ["Reprojected to EPSG:4326",
                "Registered sample centres to inclusive atlas bounds",
                f"Resampled to {job.land_mask.width}x{job.land_mask.height} using {job.land_mask.resampling}",
                f"Mapped source values {list(job.land_mask.land_values)} to land"]),
        ],
    }


def report(job: Job, version: str, elevation: dict[str, Any],
           land_mask: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "tool": {"name": "Orbis Terrae GDAL normalization tool", "version": "0.1.0",
                 "gdalVersion": version, "minimumGdalVersion": "3.8.0",
                 "projNetwork": "disabled", "gdalThreads": 1},
        "job": {"file": job.path.name, "sha256": job.sha256},
        "atlas": {"id": job.atlas["id"], "version": job.atlas["version"],
                  "targetSrs": "EPSG:4326", "bounds": job.bounds.__dict__,
                  "pixelRegistration": "sample_centres_inclusive"},
        "layers": [elevation, land_mask],
        "compilerInput": {
            "manifest": "atlas-manifest.json", "elevation": "elevation.raw",
            "landMask": "land-mask.raw",
            "command": "compile-raster-atlas atlas-manifest.json elevation.raw land-mask.raw <output>",
        },
    }


def elevation_stats(samples: Sequence[int]) -> dict[str, int | None]:
    valid = [value for value in samples if value != NO_DATA]
    return {"sampleCount": len(samples), "validSampleCount": len(valid),
            "noDataSampleCount": len(samples) - len(valid),
            "minimumMetres": min(valid) if valid else None,
            "maximumMetres": max(valid) if valid else None}


def write_elevation_preview(path: Path, width: int, height: int,
                            samples: Sequence[int], stats: dict[str, int | None]) -> None:
    low, high = stats["minimumMetres"], stats["maximumMetres"]
    pixels = bytearray()
    for value in samples:
        if value == NO_DATA or low is None or high is None:
            pixels.append(0)
        elif low == high:
            pixels.append(255)
        else:
            pixels.append(1 + round((value - low) * 254 / (high - low)))
    write_pgm(path, width, height, bytes(pixels), "elevation")


def write_pgm(path: Path, width: int, height: int, pixels: bytes, label: str) -> None:
    if len(pixels) != width * height:
        raise Error("Preview dimensions do not match pixels")
    path.write_bytes(f"P5\n# Orbis Terrae {label}\n{width} {height}\n255\n".encode() + pixels)


def write_checksums(root: Path) -> None:
    (root / "checksums.sha256").write_text(
        "".join(f"{sha256(root / name)}  {name}\n" for name in FILES),
        encoding="utf-8", newline="\n")


def verify(root: Path) -> None:
    root = root.resolve()
    entries: dict[str, str] = {}
    for line in (root / "checksums.sha256").read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([^/\\]+)", line)
        if not match:
            raise Error(f"Invalid checksum line: {line}")
        entries[match.group(2)] = match.group(1)
    if set(entries) != set(FILES):
        raise Error("Checksum file set is incomplete")
    for name, expected in entries.items():
        if sha256(root / name) != expected:
            raise Error(f"Checksum mismatch for {name}")
    doc = json.loads((root / "atlas-manifest.json").read_text(encoding="utf-8"))
    layers = {layer["id"]: layer for layer in doc["layers"]}
    elevation_size = layers["elevation"]["gridWidthSamples"] * layers["elevation"]["gridHeightSamples"] * 2
    mask_size = layers["land_mask"]["gridWidthSamples"] * layers["land_mask"]["gridHeightSamples"]
    if (root / "elevation.raw").stat().st_size != elevation_size:
        raise Error("Elevation raw size does not match manifest")
    mask = (root / "land-mask.raw").read_bytes()
    if len(mask) != mask_size or any(value not in (0, 1) for value in mask):
        raise Error("Land-mask raw violates dimensions or 0/1 contract")


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8", newline="\n")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fmt(value: float) -> str:
    return format(value, ".17g")


def require_dict(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise Error(f"{name} must be an object")
    return value


def text(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise Error(f"{name} must be a non-blank string")
    return value


def integer(value: Any, name: str, minimum: int, maximum: int | None = None) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise Error(f"{name} must be an integer")
    if value < minimum or maximum is not None and value > maximum:
        raise Error(f"{name} is outside the allowed range")
    return value


def number(value: Any, name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise Error(f"{name} must be a number")
    result = float(value)
    if not float("-inf") < result < float("inf"):
        raise Error(f"{name} must be finite")
    return result


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    commands = result.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("plan")
    plan.add_argument("job", type=Path)
    normalize_cmd = commands.add_parser("normalize")
    normalize_cmd.add_argument("job", type=Path)
    normalize_cmd.add_argument("output", type=Path)
    normalize_cmd.add_argument("--gdal-bin", type=Path)
    normalize_cmd.add_argument("--keep-intermediate", action="store_true")
    verify_cmd = commands.add_parser("verify")
    verify_cmd.add_argument("output", type=Path)
    return result


def main(arguments: Sequence[str] | None = None) -> int:
    args = parser().parse_args(arguments)
    try:
        if args.command == "verify":
            verify(args.output)
            print(f"Valid normalized raster output {args.output.resolve()}")
            return 0
        job = load_job(args.job)
        if args.command == "plan":
            print(json.dumps({
                "schemaVersion": 1,
                "atlasId": job.atlas["id"],
                "targetSrs": "EPSG:4326",
                "pixelRegistration": "sample_centres_inclusive",
                "elevation": {"source": job.elevation.source_text,
                              "grid": geometry(job.bounds, job.elevation.width, job.elevation.height),
                              "resampling": job.elevation.resampling},
                "landMask": {"source": job.land_mask.source_text,
                             "grid": geometry(job.bounds, job.land_mask.width, job.land_mask.height),
                             "resampling": job.land_mask.resampling,
                             "landValues": list(job.land_mask.land_values)},
            }, indent=2))
            return 0
        normalize(job, args.output, args.gdal_bin, args.keep_intermediate)
        print(f"Normalized raster inputs to {args.output.resolve()}")
        return 0
    except (Error, OSError, KeyError, ValueError) as exc:
        print(f"error: {exc}", file=os.sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
