from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("build_northern_europe_detailed_atlas.py")
SPEC = importlib.util.spec_from_file_location("build_northern_europe_detailed_atlas", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load Northern Europe atlas builder")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class NorthernEuropeDetailedAtlasBuilderTest(unittest.TestCase):
    def profile_document(self) -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "datasetId": "test-regional-atlas",
            "atlasVersion": "1.0.0",
            "compilerVersion": "0.1.0-SNAPSHOT",
            "bounds": {"west": 10.0, "south": 60.0, "east": 11.0, "north": 61.0},
            "resolutionArcSeconds": 15,
            "widthSamples": 241,
            "heightSamples": 241,
            "tileSize": 64,
            "sourceLock": "sources.lock.json",
            "outputArchive": "test-regional-atlas.zip",
            "elevation": {"resampling": "bilinear", "maskWaterToZero": True},
            "landMask": {"resampling": "near", "landValues": [1]},
        }

    def lock_document(self) -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "datasetId": "test-regional-atlas",
            "sources": [
                {
                    "id": "etopo-2022-15s-n75-e000",
                    "fileName": "etopo.tif",
                    "url": "https://example.invalid/etopo.tif",
                    "sha256": "0" * 64,
                    "dataset": "ETOPO 2022",
                    "datasetVersion": "2022 v1",
                    "licence": "CC0-1.0",
                    "attribution": "NOAA NCEI",
                },
                {
                    "id": "natural-earth-10m-land",
                    "fileName": "land.zip",
                    "url": "https://example.invalid/land.zip",
                    "sha256": "1" * 64,
                    "dataset": "Natural Earth Land",
                    "datasetVersion": "5.1.1",
                    "licence": "Public domain",
                    "attribution": "Made with Natural Earth",
                },
            ],
        }

    def write_contracts(self, directory: Path) -> tuple[Path, Path]:
        profile_path = directory / "profile.json"
        lock_path = directory / "sources.lock.json"
        profile_path.write_text(json.dumps(self.profile_document()), encoding="utf-8")
        lock_path.write_text(json.dumps(self.lock_document()), encoding="utf-8")
        return profile_path, lock_path

    def test_profile_enforces_inclusive_fifteen_arc_second_grid(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            profile_path, _ = self.write_contracts(Path(directory))
            profile = MODULE.load_profile(profile_path)

        self.assertEqual(241, profile.width)
        self.assertEqual(241, profile.height)
        self.assertEqual((4, 4), MODULE.tile_grid(profile))
        self.assertEqual(
            (9.997916666666667, 59.99791666666667, 11.002083333333333, 61.00208333333333),
            MODULE.sample_edge_bounds(profile),
        )

    def test_profile_rejects_dimensions_that_do_not_match_resolution(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            document = self.profile_document()
            document["widthSamples"] = 240
            profile_path = root / "profile.json"
            profile_path.write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(MODULE.Error, "dimensions"):
                MODULE.load_profile(profile_path)

    def test_lock_requires_etopo_and_exactly_one_land_archive(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            profile_path, lock_path = self.write_contracts(Path(directory))
            profile = MODULE.load_profile(profile_path)
            _, sources = MODULE.load_lock(profile, lock_path)
            self.assertEqual(2, len(sources))

            document = self.lock_document()
            document["sources"] = document["sources"][:1]
            lock_path.write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaisesRegex(MODULE.Error, "Natural Earth"):
                MODULE.load_lock(profile, lock_path)

    def test_plan_reports_release_scale_sizes_and_unlocked_sources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path, lock_path = self.write_contracts(root)
            document = self.lock_document()
            document["sources"][0]["sha256"] = None
            lock_path.write_text(json.dumps(document), encoding="utf-8")
            profile = MODULE.load_profile(profile_path)
            result = MODULE.plan(profile)

        self.assertEqual(58081, result["sampleCountPerLayer"])
        self.assertEqual(116162, result["elevationRawBytes"])
        self.assertEqual(16, result["tilesPerLayer"])
        self.assertEqual(["etopo-2022-15s-n75-e000"], result["unlockedSources"])

    def test_normalization_job_records_water_mask_and_reviewed_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile_path, _ = self.write_contracts(root)
            profile = MODULE.load_profile(profile_path)
            source_directory = root / "sources"
            source_directory.mkdir()
            elevation = source_directory / "elevation.tif"
            land = source_directory / "land.tif"
            job = MODULE.normalization_job(profile, elevation, land, root)

        self.assertEqual(profile.dataset_id, job["atlas"]["id"])
        self.assertEqual(profile.bounds.__dict__, job["atlas"]["bounds"])
        self.assertEqual(MODULE.NO_DATA, job["elevation"]["sourceNoData"])
        self.assertIn("ETOPO 2022", job["elevation"]["provenance"]["title"])
        self.assertIn(
            "ocean elevation samples are zero",
            job["elevation"]["provenance"]["processing"][-1],
        )
        self.assertEqual([1], job["landMask"]["landValues"])

    def test_deterministic_archive_has_sorted_fixed_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            atlas = root / "atlas"
            (atlas / "nested").mkdir(parents=True)
            (atlas / "z.txt").write_text("z\n", encoding="utf-8")
            (atlas / "nested/a.txt").write_text("a\n", encoding="utf-8")
            first = root / "first.zip"
            second = root / "second.zip"
            MODULE.write_archive(atlas, first)
            MODULE.write_archive(atlas, second)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(["nested/a.txt", "z.txt"], archive.namelist())
                self.assertTrue(
                    all(info.date_time == MODULE.ZIP_TIMESTAMP for info in archive.infolist())
                )

    def test_format_number_is_locale_independent(self) -> None:
        self.assertEqual("-25", MODULE.format_number(-25.0))
        self.assertEqual("54.0020833333333", MODULE.format_number(54.00208333333333))


if __name__ == "__main__":
    unittest.main()
