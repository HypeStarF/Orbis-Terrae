from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("build_bergen_real_atlas.py")
SPEC = importlib.util.spec_from_file_location("build_bergen_real_atlas", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load Bergen atlas builder")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class BergenRealAtlasBuilderTest(unittest.TestCase):
    def test_sample_edge_bounds_preserve_inclusive_sample_centres(self) -> None:
        west, south, east, north = MODULE.sample_edge_bounds()
        x_resolution = (MODULE.BOUNDS["east"] - MODULE.BOUNDS["west"]) / (
            MODULE.WIDTH - 1
        )
        y_resolution = (MODULE.BOUNDS["north"] - MODULE.BOUNDS["south"]) / (
            MODULE.HEIGHT - 1
        )
        self.assertAlmostEqual(MODULE.BOUNDS["west"], west + x_resolution / 2)
        self.assertAlmostEqual(MODULE.BOUNDS["east"], east - x_resolution / 2)
        self.assertAlmostEqual(MODULE.BOUNDS["south"], south + y_resolution / 2)
        self.assertAlmostEqual(MODULE.BOUNDS["north"], north - y_resolution / 2)

    def test_load_lock_accepts_bootstrap_hashes_and_rejects_wrong_contract(self) -> None:
        document = {
            "schemaVersion": 1,
            "datasetId": MODULE.DATASET_ID,
            "bounds": MODULE.BOUNDS,
            "widthSamples": MODULE.WIDTH,
            "heightSamples": MODULE.HEIGHT,
            "tileSize": MODULE.TILE_SIZE,
            "sources": [
                {
                    "id": "copernicus-dem-glo90-n60-e004",
                    "fileName": "a.tif",
                    "url": "https://example.invalid/a.tif",
                    "sha256": None,
                },
                {
                    "id": "copernicus-dem-glo90-n60-e005",
                    "fileName": "b.tif",
                    "url": "https://example.invalid/b.tif",
                    "sha256": "0" * 64,
                },
                {
                    "id": "natural-earth-10m-land",
                    "fileName": "land.zip",
                    "url": "https://example.invalid/land.zip",
                    "sha256": "1" * 64,
                },
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lock.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            loaded, sources = MODULE.load_lock(path)
            self.assertEqual(document, loaded)
            self.assertEqual(3, len(sources))
            self.assertIsNone(sources[0].sha256)

            document["widthSamples"] = MODULE.WIDTH + 1
            path.write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(MODULE.Error, "dimensions"):
                MODULE.load_lock(path)

    def test_normalization_job_contains_reviewed_provenance_and_grid(self) -> None:
        workspace = Path("/tmp/bergen-workspace")
        job = MODULE.normalization_job(
            workspace / "sources/copernicus-dem-mosaic.vrt",
            workspace / "sources/natural-earth-land-mask.tif",
            workspace,
        )
        self.assertEqual(MODULE.DATASET_ID, job["atlas"]["id"])
        self.assertEqual(MODULE.BOUNDS, job["atlas"]["bounds"])
        self.assertEqual(MODULE.WIDTH, job["elevation"]["widthSamples"])
        self.assertEqual(MODULE.HEIGHT, job["landMask"]["heightSamples"])
        self.assertIn("Copernicus WorldDEM-90", job["elevation"]["provenance"]["attribution"])
        self.assertEqual("Public domain", job["landMask"]["provenance"]["licence"])
        self.assertEqual([1], job["landMask"]["landValues"])

    def test_format_number_is_locale_independent(self) -> None:
        self.assertEqual("4.75", MODULE.format_number(4.75))
        self.assertEqual("60.2", MODULE.format_number(60.2))


if __name__ == "__main__":
    unittest.main()
