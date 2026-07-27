# Phase 2 Step 8: terrain benchmark and exit audit

Step 8 measures the first atlas-backed terrain pipeline and audits Phase 2 against the approved master-plan
criteria. It does not add hydrology, caves, vegetation, climate, or new terrain-shaping behavior.

The audit is deliberately not a declaration that Phase 2 is complete. Local client inspection of the bundled Bergen
atlas exposed severe horizontal compression and pillar-like mountain forms. Those findings block the
recognisable-Scandinavia criterion and require corrective terrain work before Phase 3 begins.

## Benchmark scope

`TerrainPipelineBenchmark` measures the Minecraft-independent work used to plan one chunk:

1. map absolute block coordinates to latitude and longitude;
2. select and sample the atlas stack;
3. bilinearly sample elevation;
4. sample the nearest land mask;
5. apply the active vertical curve;
6. create all 256 `TerrainColumnPlan` values;
7. hash the complete deterministic chunk plan.

The checked workload uses the Global Survival profile around the configured Bergen spawn:

| Property | Value |
| --- | ---: |
| Workload area | 3 x 3 chunks |
| Columns per chunk | 256 |
| Warm-up passes | 1 |
| Measured passes | 5 |
| Measured chunks | 45 |
| Measured columns | 11,520 |
| Provisional p95 target | 150 ms per planned chunk |

The 150 ms target is inherited from the master plan's initial Global Survival chunk-generation target. Passing this
planning benchmark is necessary but not sufficient: it excludes block writes, heightmap priming, lighting, chunk
serialization, server scheduling, networking, and concurrent player exploration.

The report records machine-labelled latency values, columns per second, a portable workload fingerprint, and a
portable result fingerprint. Absolute timings should only be compared between similar machines and software
environments.

## Terrain continuity and shape audit

`TerrainContinuityAudit` samples a 97 x 49 block window around Bergen and records:

- complete, land, ocean, and incomplete atlas-backed columns;
- adjacent land-column height steps;
- chunk-boundary and interior step distributions separately;
- land-neighbor pairs with a height difference greater than eight blocks;
- isolated peaks more than eight blocks above all four orthogonal land neighbors;
- a deterministic fingerprint of the audited terrain window.

The provisional terrain-quality signal allows at most two percent of adjacent land pairs to exceed the eight-block
step threshold and allows no isolated peaks. The signal is diagnostic in Step 8 rather than a build failure. The
current terrain has already failed visual acceptance, so CI must continue producing the report while corrective work
changes the metrics.

## Reproducing the reports

Linux or macOS:

```bash
TERRAIN_BENCHMARK_REPORT="$PWD/modules/minecraft-mod/build/terrain-benchmark/report.json" \
TERRAIN_CONTINUITY_REPORT="$PWD/modules/minecraft-mod/build/terrain-benchmark/continuity.json" \
  ./gradlew phase2PerformanceExitAuditCheck \
  --no-configuration-cache \
  --warning-mode=fail
```

Windows PowerShell:

```powershell
$env:TERRAIN_BENCHMARK_REPORT = \
  "$PWD\modules\minecraft-mod\build\terrain-benchmark\report.json"
$env:TERRAIN_CONTINUITY_REPORT = \
  "$PWD\modules\minecraft-mod\build\terrain-benchmark\continuity.json"

.\gradlew.bat phase2PerformanceExitAuditCheck `
  --no-configuration-cache `
  --warning-mode=fail
```

The dedicated `Terrain Pipeline Benchmark` workflow uploads both JSON reports for pull requests that change the
terrain pipeline, atlas runtime, Bergen fixture, or Phase 2 documentation.

## Why the current Bergen terrain is pillar-like

The current runtime content and profile combine three limitations:

1. The bundled atlas covers only longitude 4.75 E to 5.75 E and latitude 60.2 N to 60.8 N. It is a Bergen fixture,
   not the Northern Europe development atlas required by the master plan.
2. Global Survival uses approximately 1,000 horizontal metres per block.
3. Its vertical curve maps 500 real metres to 55 blocks and 2,000 real metres to 155 blocks above sea level.

A steep Norwegian slope can therefore change by dozens of Minecraft blocks while moving only one or two blocks
horizontally. The current generator samples each column directly and has no scale-aware terrain generalisation,
low-pass reconstruction, slope policy, ridge-width preservation, or hydrological correction. The resulting spikes
are a predictable prototype limitation, not acceptable final terrain.

## Phase 2 exit audit

| Master-plan criterion | Evidence | Status |
| --- | --- | --- |
| The game creates an Orbis Terrae world. | The Earth preset decodes in headless NeoForge, and local client testing created the photographed Bergen world. | Passed |
| Scandinavia has recognisable large-scale land and sea terrain. | Only the small Bergen fixture is bundled. Local inspection shows a small atlas patch surrounded by fallback ocean and pillar-like relief. | **Blocked** |
| The same seed and immutable manifest reproduce identical chunks. | Stable chunk fingerprints, independent atlas installations, strict manifest hashes, and disk round-trip tests. | Passed |
| Singleplayer and dedicated server both work. | Client and server launch preparation plus headless server startup pass. A recorded full client save/reopen and interactive dedicated-server restart remain required. | Partial |
| No runtime internet access is required. | The production JAR contains the reviewed Bergen atlas and the runtime uses only local atlas files. | Passed |
| No obvious chunk-border elevation seams are visible. | The audit separates boundary and interior step distributions, but current terrain roughness prevents visual sign-off. | **Blocked** |
| Atlas lookups and terrain planning are sufficiently fast. | The portable atlas benchmark already passes its warm lookup target; Step 8 adds the first terrain-planning p95 report. Full Minecraft chunk cost remains unmeasured. | Partial |
| Atlas provenance is documented. | Bergen and regional build inputs use source locks, checksums, attribution, and deterministic rebuild workflows. | Passed |
| Atlas and coordinate debugging commands exist. | Debug-screen information exists, but the master-plan `/orbis coords` and atlas-status commands are not implemented. | **Blocked** |

## Exit decision

**Phase 2 remains open. Phase 3 hydrology must not begin yet.**

The following corrective work is required first:

1. Package a real Northern Europe runtime atlas covering the intended Scandinavian development region rather than
   only the Bergen fixture.
2. Add scale-aware terrain reconstruction/generalisation so coarse horizontal profiles produce broad slopes and
   ridges instead of one-column pillars.
3. Preserve coastlines and major landforms while applying the reconstruction filter.
4. Add geographic regression outputs for Norwegian fjords, southern Sweden, Finland, Denmark, Iceland, and the
   Baltic coast.
5. Implement the initial coordinate and atlas diagnostic commands.
6. Record successful client save/reopen and dedicated-server stop/restart acceptance results.
7. Repeat the benchmark, continuity audit, and visual review before closing Phase 2.

## Scope boundary

Step 8 establishes measurements and an evidence-based gate. It does not silently smooth terrain or lower mountains,
because terrain reconstruction must be designed and tested against coastline and geographic-recognition criteria.
The next Phase 2 step is corrective terrain generalisation and Northern Europe runtime coverage, not hydrology.
