from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("build_global_coarse_atlas.py")
SPEC = importlib.util.spec_from_file_location("build_global_coarse_atlas", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load global coarse atlas builder")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class GlobalCoarseAtlasBuilderTest(unittest.TestCase):
    def test_release_profile_covers_global_pixel_edges(self) -> None:
        profile = MODULE.load_profile(MODULE.DEFAULT_PROFILE)

        self.assertEqual("global-coarse-v1", profile.dataset_id)
        self.assertEqual(300, profile.resolution_arc_seconds)
        self.assertEqual(4320, profile.width)
        self.assertEqual(2160, profile.height)
        self.assertEqual((17, 9), MODULE.tile_grid(profile))
        west, south, east, north = MODULE.sample_edge_bounds(profile)
        self.assertAlmostEqual(-180.0, west, places=12)
        self.assertAlmostEqual(-90.0, south, places=12)
        self.assertAlmostEqual(180.0, east, places=12)
        self.assertAlmostEqual(90.0, north, places=12)

    def test_plan_reports_global_fallback_scale(self) -> None:
        profile = MODULE.load_profile(MODULE.DEFAULT_PROFILE)
        result = MODULE.plan(profile)

        self.assertEqual(9_331_200, result["sampleCountPerLayer"])
        self.assertEqual(18_662_400, result["elevationRawBytes"])
        self.assertEqual(9_331_200, result["landMaskRawBytes"])
        self.assertEqual(153, result["tilesPerLayer"])
        self.assertEqual(2, result["sourceCount"])
        self.assertTrue(
            set(result["unlockedSources"]).issubset({"etopo-2022-60s-global-surface"})
        )

    def test_lock_requires_exactly_one_60s_surface_raster(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_document = json.loads(MODULE.DEFAULT_PROFILE.read_text(encoding="utf-8"))
            profile_document["sourceLock"] = "sources.lock.json"
            profile_path = root / "profile.json"
            profile_path.write_text(json.dumps(profile_document), encoding="utf-8")

            lock_document = json.loads(
                (MODULE.REPO_ROOT / "atlas/datasets/global-coarse-v1/sources.lock.json").read_text(
                    encoding="utf-8"
                )
            )
            lock_document["sources"][0]["id"] = "etopo-2022-15s-n60-e000"
            lock_path = root / "sources.lock.json"
            lock_path.write_text(json.dumps(lock_document), encoding="utf-8")

            profile = MODULE.load_profile(profile_path)
            with self.assertRaisesRegex(MODULE.Error, "60s surface raster"):
                MODULE.load_lock(profile, lock_path)

    def test_normalization_job_records_global_cell_registration(self) -> None:
        profile = MODULE.load_profile(MODULE.DEFAULT_PROFILE)
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            source_directory = workspace / "sources"
            source_directory.mkdir()
            job = MODULE.normalization_job(
                profile,
                source_directory / "elevation.tif",
                source_directory / "land-mask.tif",
                workspace,
            )

        provenance = job["elevation"]["provenance"]
        self.assertEqual("ETOPO 2022 60 Arc-Second Surface Elevation", provenance["title"])
        self.assertIn("5 arc-minute cell-centred samples", provenance["processing"][1])
        self.assertIn("180°W–180°E", provenance["processing"][2])
        self.assertIn("ocean elevation samples are zero", provenance["processing"][3])
        self.assertEqual([1], job["landMask"]["landValues"])

    def test_global_source_identifier_is_unambiguous(self) -> None:
        self.assertTrue(MODULE.is_etopo("etopo-2022-60s-global-surface"))
        self.assertFalse(MODULE.is_etopo("etopo-2022-15s-n60-e000"))


if __name__ == "__main__":
    unittest.main()
