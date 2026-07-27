# Phase 1 Step 11: performance benchmark and exit audit

Phase 1 Step 11 records the first repeatable atlas-runtime benchmark and audits the atlas proof of
concept against the master-plan exit criteria. It does not claim to measure Minecraft chunk generation,
ten-player exploration, lighting, networking, or a long-running dedicated server. Those require later
world-generation and game-integration phases.

## Benchmark scope

`AtlasRuntimeBenchmark` measures the Java runtime reader against ordinary OTAT files on disk. The checked
benchmark fixture contains:

| Property | Value |
| --- | ---: |
| Elevation grid | 64 x 64 samples |
| Tile size | 8 x 8 samples |
| Elevation tiles | 64 |
| Configured cache capacity | 4 tiles |
| Cold operations | 64 |
| Warm operations | 5,000 |
| Deterministic random operations | 2,000 |
| Parallel workers | 4 |
| Parallel operations per worker | 1,000 |

The fixture is intentionally larger than the cache. Random sampling therefore forces repeated LRU
eviction rather than measuring an atlas that remains completely resident.

## Measurement definitions

### Cold nearest sampling

The Orbis Terrae tile cache is cleared before every operation. The measurement includes path resolution,
file access, CRC validation, OTAT decoding, manifest compatibility checks, and cache insertion.

This is **application-cache cold**, not guaranteed storage-device cold. The operating system and disk may
retain file-system pages. Reproducing physical cold-storage latency would require machine-specific cache
control and is not suitable for portable CI.

### Warm nearest sampling

One hotspot is loaded before timing. Five thousand deterministic coordinates remain in a very small area,
so the measurement represents repeated lookup from an already decoded tile.

The master plan's initial target is warm atlas lookup below approximately 2 ms at the 95th percentile.
The benchmark records the exact p95 and fails when this target is exceeded.

### Random nearest sampling

Two thousand deterministic coordinates span the complete 64 x 64 grid. The workload fingerprint is
independent of wall-clock timing and must remain stable across repeated runs. Its result fingerprint also
must remain stable, proving that cache state and eviction do not alter sampled values.

### Parallel stress

Four threads share one `AtlasDirectory` and `ElevationSampler`. Every worker uses a deterministic random
stream. The combined result checksum must be identical on repeated runs, and the cache must remain within
the configured four-tile limit.

## Memory interpretation

The benchmark does not use heap-delta assertions because garbage collection, class loading, JIT state, and
VM implementation make small heap differences unreliable in CI. Instead it records two exact invariants:

1. The maximum observed number of resident cache entries must not exceed the configured capacity.
2. The decoded elevation-array payload bound is calculated as:

```text
cache tiles x tile size x tile size x 2 bytes
```

For the checked fixture this is `4 x 8 x 8 x 2 = 512` bytes of elevation sample payload. Java object,
array, map, and cache-key overhead exists in addition to this payload, but remains proportional to the
fixed entry limit rather than atlas size. The complete atlas is never loaded into memory.

## Reproducing the benchmark

The benchmark is included in ordinary tests and has a dedicated report workflow:

```bash
./gradlew :modules:atlas-api:test \
  --tests me.sdmannen.orbis_terrae.atlas.benchmark.AtlasRuntimeBenchmarkTest \
  --no-configuration-cache \
  --warning-mode=fail
```

Set `ATLAS_BENCHMARK_REPORT` to an absolute path to write the JSON report:

```bash
ATLAS_BENCHMARK_REPORT="$PWD/modules/atlas-api/build/atlas-benchmark/report.json" \
  ./gradlew :modules:atlas-api:test \
  --tests me.sdmannen.orbis_terrae.atlas.benchmark.AtlasRuntimeBenchmarkTest \
  --no-configuration-cache \
  --warning-mode=fail
```

`.github/workflows/atlas-runtime-benchmark.yml` runs this command on Java 21 and uploads the JSON report.
Absolute timings should be compared only between similar machines and software environments. Workload and
result fingerprints are the portable determinism signals.

## Initial CI baseline

The first successful report was recorded on GitHub's Ubuntu 24.04 runner with Temurin Java 21.0.11.
Nanosecond values are retained in the uploaded JSON; the table below converts the percentile values for
readability.

| Workload | p50 | p95 | p99 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Application-cache-cold nearest | 138.551 us | 204.088 us | 3.214 ms | 3.214 ms |
| Warm nearest | 1.954 us | 7.305 us | 20.600 us | 2.590 ms |
| Random nearest | 42.529 us | 109.174 us | 149.720 us | 287.127 us |

The warm p95 was `7,305 ns`, approximately 274 times below the `2,000,000 ns` target. The isolated warm
maximum includes scheduler/JIT noise and is not the gating percentile.

| Deterministic/cache signal | Value |
| --- | --- |
| Workload fingerprint | `5c3ebdffa74a1fc0` |
| Result fingerprint | `99edc292c637bcf4` |
| Cache hits | 5,136 |
| Cache misses | 1,929 |
| Final cache size | 4 tiles |
| Maximum observed cache size | 4 tiles |
| Decoded elevation payload bound | 512 bytes |

## Phase 1 exit-criteria audit

| Master-plan exit criterion | Evidence | Status |
| --- | --- | --- |
| A standalone test program samples elevation by latitude and longitude. | `ElevationSampler`, the compiler sampling CLI, geographic sampling tests, and this standalone runtime benchmark all sample by latitude/longitude without Minecraft. | Passed |
| Adjacent tile boundaries are continuous. | Synthetic four-tile interpolation tests, compiler-generated seam tests, and the real Bergen four-tile interpolation assertion cover horizontal, vertical, and four-way boundaries. | Passed |
| Atlas output is deterministic. | Canonical synthetic fixture verification, deterministic ZIP generation, pinned-source Bergen/regional/global rebuild comparisons, stable benchmark workload fingerprints, and repeatable result fingerprints. | Passed |
| Corrupt tiles are detected. | OTAT CRC validation, corrupt-tile reader tests, fixture verification, and runtime fallback tests reject or bypass corrupted preferred tiles. | Passed |
| Runtime reader requires no GIS libraries. | `atlas-api` reads manifests and OTAT files using Java runtime code only. GDAL remains confined to offline build scripts and external-data workflows. Linux and Windows sampling tests run without Java GIS bindings. | Passed |

## Additional runtime findings

- The tile cache remains entry-bounded while traversing more tiles than it can retain.
- Warm lookup is measured separately from decode and file access.
- Sampling results remain deterministic after LRU eviction.
- One shared reader survives concurrent sampling with repeatable results.
- Performance reports include the Java and operating-system versions to avoid treating unlike machines as
  directly comparable.

## Deferred work

The following master-plan performance tests are outside Phase 1 because their systems do not yet exist:

- Minecraft chunk-generation CPU time
- Main-thread stall measurements
- Pregeneration throughput
- Ten-player exploration and 20 TPS validation
- Weather-cell and simulation scaling
- Large-height lighting performance
- Dedicated-server memory and soak testing

These must be added incrementally when the relevant Phase 2 and later systems are implemented. Phase 1
closes only the offline atlas format, build pipeline, runtime reader, cache, coordinate sampling, regional
selection, and their measured standalone behavior.
