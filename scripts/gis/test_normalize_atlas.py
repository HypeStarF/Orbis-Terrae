import json
import shutil
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import normalize_atlas as tool


class FakeRun:
    def __init__(self):
        self.commands = []

    def __call__(self, arguments):
        command = list(arguments)
        self.commands.append(command)
        name = Path(command[0]).stem.lower()
        if name == "gdalinfo" and command[1:] == ["--version"]:
            return "GDAL 3.11.4\n"
        if name == "gdalwarp":
            Path(command[-1]).write_bytes(b"warped")
            return ""
        if name == "gdalinfo":
            return json.dumps({
                "driverShortName": "GTiff",
                "size": [3, 2],
                "geoTransform": [-0.5, 1, 0, 1.5, 0, -1],
                "bands": [{"type": "Int16", "noDataValue": -32768, "checksum": 12}],
            })
        if name == "gdal_translate":
            output = Path(command[-1])
            if "elevation" in output.name:
                values = (10, 20, 30, 40, 50, tool.NO_DATA)
                output.write_bytes(b"".join(struct.pack(">h", value) for value in values))
                data_type, byte_order, samples, lines = 2, 1, 3, 2
            else:
                output.write_bytes(b"\x00\x00" + bytes((0, 5, 9, 5, 0, 9)))
                data_type, byte_order, samples, lines = 1, 0, 2, 3
            output.with_suffix(".hdr").write_text(
                "\n".join([
                    "ENVI",
                    f"samples = {samples}",
                    f"lines = {lines}",
                    "bands = 1",
                    f"header offset = {2 if data_type == 1 else 0}",
                    f"data type = {data_type}",
                    "interleave = bsq",
                    f"byte order = {byte_order}",
                ]),
                encoding="ascii",
            )
            return ""
        raise AssertionError(command)


class NormalizeAtlasTest(unittest.TestCase):
    def test_geometry_registers_inclusive_sample_centres(self):
        grid = tool.geometry(tool.Bounds(0, 0, 2, 1), 3, 2)
        self.assertEqual(1.0, grid["xResolutionDegrees"])
        self.assertEqual(
            {"west": -0.5, "south": -0.5, "east": 2.5, "north": 1.5},
            grid["pixelEdgeBounds"],
        )

    def test_big_endian_envi_becomes_little_endian(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            binary = root / "elevation.bin"
            header = root / "elevation.hdr"
            values = (-32768, -4, 5, 300)
            binary.write_bytes(b"".join(struct.pack(">h", value) for value in values))
            header.write_text(
                "\n".join([
                    "ENVI", "samples = 2", "lines = 2", "bands = 1",
                    "header offset = 0", "data type = 2",
                    "interleave = bsq", "byte order = 1",
                ]),
                encoding="ascii",
            )
            parsed = tool.parse_envi(header, 2, 2, 2)
            payload = tool.read_payload(binary, parsed, 8)
            converted = b"".join(
                payload[index:index + 2][::-1] for index in range(0, len(payload), 2)
            )
            self.assertEqual(
                values,
                tuple(value[0] for value in struct.iter_unpack("<h", converted)),
            )

    def test_full_fake_normalization_is_verifiable(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            job_path = self.write_job(root, write_sources=True)
            output = root / "normalized"
            fake = FakeRun()
            with mock.patch.object(tool, "run", fake):
                tool.normalize(tool.load_job(job_path), output, None, False)

            tool.verify(output)
            self.assertEqual(
                (10, 20, 30, 40, 50, tool.NO_DATA),
                tuple(value[0] for value in struct.iter_unpack(
                    "<h", (output / "elevation.raw").read_bytes()
                )),
            )
            self.assertEqual(
                bytes((0, 1, 0, 1, 0, 0)),
                (output / "land-mask.raw").read_bytes(),
            )
            self.assertTrue((output / "checksums.sha256").is_file())
            warp = next(command for command in fake.commands
                        if Path(command[0]).stem.lower() == "gdalwarp")
            position = warp.index("-te")
            self.assertEqual(
                ["-0.5", "-0.5", "2.5", "1.5"],
                warp[position + 1:position + 5],
            )
            self.assertIn("-q", warp)

    def test_verify_rejects_changed_raw_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "normalized"
            with mock.patch.object(tool, "run", FakeRun()):
                tool.normalize(tool.load_job(self.write_job(root, True)), output, None, False)
            path = output / "land-mask.raw"
            path.write_bytes(b"\x01" + path.read_bytes()[1:])
            with self.assertRaisesRegex(tool.Error, "Checksum mismatch"):
                tool.verify(output)

    def test_failure_removes_partial_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "normalized"
            with mock.patch.object(tool, "run", return_value="GDAL 3.7.3\n"):
                with self.assertRaisesRegex(tool.Error, "3.8.0"):
                    tool.normalize(
                        tool.load_job(self.write_job(root, True)), output, None, False
                    )
            self.assertFalse(output.exists())

    @unittest.skipUnless(
        all(shutil.which(name) for name in ("gdalwarp", "gdal_translate", "gdalinfo")),
        "GDAL command-line tools are not installed",
    )
    def test_real_gdal_smoke(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            tool.write_pgm(
                root / "elevation.pgm", 3, 2,
                bytes((10, 20, 30, 40, 50, 60)), "smoke elevation"
            )
            tool.write_pgm(
                root / "land.pgm", 2, 3,
                bytes((0, 5, 9, 5, 0, 9)), "smoke land"
            )
            subprocess.run([
                "gdal_translate", "-q", "-of", "GTiff", "-a_srs", "EPSG:4326",
                "-a_ullr", "-0.5", "1.5", "2.5", "-0.5",
                str(root / "elevation.pgm"), str(root / "elevation.tif"),
            ], check=True)
            subprocess.run([
                "gdal_translate", "-q", "-of", "GTiff", "-a_srs", "EPSG:4326",
                "-a_ullr", "-1", "1.25", "3", "-0.25",
                str(root / "land.pgm"), str(root / "land-mask.tif"),
            ], check=True)
            output = root / "normalized"
            tool.normalize(tool.load_job(self.write_job(root, False)), output, None, False)
            tool.verify(output)
            self.assertEqual(bytes((0, 1, 0, 1, 0, 0)),
                             (output / "land-mask.raw").read_bytes())

    @staticmethod
    def write_job(root, write_sources):
        if write_sources:
            (root / "elevation.tif").write_bytes(b"elevation")
            (root / "land-mask.tif").write_bytes(b"land")
        document = {
            "schemaVersion": 1,
            "atlas": {
                "id": "test-atlas",
                "version": "1.0.0",
                "compilerVersion": "0.1.0-SNAPSHOT",
                "bounds": {"west": 0, "south": 0, "east": 2, "north": 1},
                "tileSize": 2,
            },
            "elevation": {
                "source": "elevation.tif",
                "widthSamples": 3,
                "heightSamples": 2,
                "sourceNoData": -9999,
                "resampling": "bilinear",
                "provenance": NormalizeAtlasTest.provenance("elevation-source"),
            },
            "landMask": {
                "source": "land-mask.tif",
                "widthSamples": 2,
                "heightSamples": 3,
                "sourceNoData": 0,
                "resampling": "near",
                "landValues": [5],
                "provenance": NormalizeAtlasTest.provenance("land-source"),
            },
        }
        path = root / "job.json"
        path.write_text(json.dumps(document), encoding="utf-8")
        return path

    @staticmethod
    def provenance(source_id):
        return {
            "sourceId": source_id,
            "title": "Synthetic source",
            "datasetVersion": "1",
            "licence": "Project test data",
            "attribution": "Orbis Terrae tests",
            "sourceUrl": "https://orbis-terrae.invalid/source",
            "retrievedDate": "2026-07-26",
            "processing": ["Created synthetic source"],
        }


if __name__ == "__main__":
    unittest.main()
