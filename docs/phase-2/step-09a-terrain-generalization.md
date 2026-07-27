# Phase 2 Step 9A: scale-aware terrain generalization

Step 9A corrects the pillar-like Bergen terrain identified by the Phase 2 exit audit. It changes only the elevation
reconstruction used by the existing land-column pipeline. Coastline classification, atlas coverage, ocean fallback,
world profiles, vertical curves, structures, spawn rules, and surface materials remain unchanged.

## Problem baseline

The original generator transformed every bilinearly sampled DEM value directly into one Minecraft column. Under the
Global Survival profile, one horizontal block represents approximately 1,000 real metres while 500 real metres of
height becomes 55 Minecraft blocks. Rugged Norwegian relief therefore changed by dozens of vertical blocks over one
horizontal block.

The Step 8 Bergen audit measured:

| Signal | Direct sampling |
| --- | ---: |
| Adjacent complete-land pairs | 4,868 |
| Pairs above an eight-block step | 1,408 |
| Steep-pair ratio | 28.924% |
| Adjacent-step p95 | 22 blocks |
| Maximum adjacent step | 47 blocks |
| Isolated peaks | 1 |
| Planning p95 | 4.836 ms/chunk |

## Reconstruction policy

`EarthAtlasSampler` now reconstructs land elevations before returning a terrain sample:

1. Sample the centre column normally.
2. Keep its exact nearest-neighbour land/water classification.
3. For complete land columns, inspect available elevation samples in a scale-aware neighborhood.
4. Use a triangular weighted kernel over a 3,000-metre radius.
5. Transform every neighboring elevation through the active vertical curve before averaging.
6. Use the weighted regional surface directly for the coarse global profiles.
7. Clamp land to sea level and the configured dimension range.
8. Leave ocean and incomplete-data columns on their previous paths.

The radius converts to three blocks under Global Survival and two blocks under Global Compact. The kernel therefore
widens real ridges and valleys at the scale visible in each profile instead of applying a fixed block-space blur.
Available water-cell elevation values participate in the regional average, but the centre land mask remains
authoritative. This produces broader coastal slopes without moving the coastline.

## Measured result

The final CI report on Java 21 produced:

| Signal | Direct sampling | Reconstructed | Change |
| --- | ---: | ---: | ---: |
| Complete columns | 4,365 | 4,365 | unchanged |
| Land columns | 2,647 | 2,647 | unchanged |
| Ocean columns | 1,718 | 1,718 | unchanged |
| Incomplete columns | 388 | 388 | unchanged |
| Pairs above an eight-block step | 1,408 | 80 | -94.3% |
| Steep-pair ratio | 28.924% | 1.643% | passed 2% gate |
| Adjacent-step p95 | 22 blocks | 7 blocks | -68.2% |
| Maximum adjacent step | 47 blocks | 12 blocks | -74.5% |
| Chunk-boundary p95 | 20 blocks | 6 blocks | -70.0% |
| Interior p95 | 22 blocks | 7 blocks | -68.2% |
| Isolated peaks | 1 | 0 | passed |
| Planning p95 | 4.836 ms | 10.744 ms | passed 150 ms gate |
| Throughput | 124,585 columns/s | 47,428 columns/s | acceptable prototype cost |

The unchanged coverage counts are asserted in CI. Reconstruction cannot pass by silently changing Bergen land,
ocean, or missing-data classification.

## Determinism and performance

The existing chunk and terrain-window fingerprints remain repeatable. The benchmark still plans 45 chunks and 11,520
columns around Bergen and enforces the provisional 150 ms p95 target.

The current implementation favors correctness and performs the neighborhood lookup for each planned land column.
Chunk-level reuse or a bounded reconstructed-height cache may be added later if full Minecraft block-placement or
multiplayer benchmarks show that the extra sampling cost matters.

## Focused verification

Linux or macOS:

```bash
./gradlew phase2PerformanceExitAuditCheck \
  --no-configuration-cache \
  --warning-mode=fail
```

Windows PowerShell:

```powershell
.\gradlew.bat phase2PerformanceExitAuditCheck `
  --no-configuration-cache `
  --warning-mode=fail
```

## Local visual acceptance

Previously generated chunks retain their old blocks. Create a new Orbis Terrae world after installing the updated
JAR, then inspect Bergen from the same broad viewpoints used for the Step 8 screenshots.

Acceptance requires:

- broad slopes and ridges instead of dense one-column pillars;
- unchanged visible coastline and island placement;
- no new chunk-border discontinuity;
- a safe land spawn near the configured Bergen target;
- no atlas, codec, generation, or save errors in `latest.log`.

## Remaining Phase 2 work

Step 9A resolves the measured Bergen terrain-shape blocker, but Phase 2 remains open. The production JAR still bundles
only the small Bergen fixture. The next increment must package meaningful Northern Europe runtime coverage and add
geographic regression outputs before diagnostic commands and the final client/server acceptance record.
