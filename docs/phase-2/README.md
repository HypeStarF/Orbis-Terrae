# Phase 2: basic Earth dimension

Phase 2 connects the completed offline atlas runtime to Minecraft world generation. The target release line
is `0.2.x — Terrain Alpha`.

## Deliverables

- Custom `BiomeSource`
- Custom `ChunkGenerator`
- Equirectangular Minecraft-to-Earth coordinate mapping
- Global Compact and Global Survival horizontal scale profiles
- Basic nonlinear vertical transformation
- Land and ocean terrain sampled from the offline atlas
- Artificial structures disabled in the Orbis Terrae dimension
- Configurable geographic spawn
- Immutable world manifest creation and validation
- Deterministic singleplayer and dedicated-server generation

## Implementation sequence

1. [World profile, projection, vertical curve, and manifest foundation](step-01-world-foundation.md)
2. [Register biome-source and chunk-generator codecs](step-02-worldgen-codecs.md)
3. [Add the Earth world preset and minimal biome selection](step-03-earth-world-preset.md)
4. [Install the bundled atlas and sample elevation and land mask](step-04-atlas-sampling.md)
5. [Fill deterministic land and ocean terrain columns](step-05-terrain-columns.md)
6. [Disable artificial structures and add safe geographic spawn selection](step-06-spawn-and-structures.md)
7. [Add chunk determinism, save/reload, client, and dedicated-server validation](step-07-determinism-and-persistence.md)
8. [Benchmark the terrain pipeline and perform the Phase 2 exit audit](step-08-performance-and-exit-audit.md)
9. Correct the Phase 2 exit blockers:
   - [9A. Add scale-aware terrain generalization](step-09a-terrain-generalization.md)
   - 9B. Package meaningful Northern Europe runtime coverage and geographic regressions
   - 9C. Add initial diagnostic commands and record final client/server acceptance

## Exit criteria

Phase 2 closes only when:

- the game creates an Orbis Terrae world;
- Scandinavia has recognisable large-scale land and sea terrain;
- the same seed and immutable manifest reproduce identical chunks;
- singleplayer and dedicated server both work;
- no runtime internet access is required.

## Current gate status

Steps 1 through 8 established immutable configuration, Minecraft serialization, data-driven world creation, offline
atlas installation, deterministic terrain columns, safe spawn selection, stable fingerprints, strict manifest
persistence, repeatable runtime preparation, and portable performance/continuity reports.

Step 9A now reconstructs coarse-profile land elevation over a scale-aware 3,000-metre radius. The Bergen steep-pair
ratio fell from 28.924% to 1.643%, adjacent-step p95 fell from 22 to 7 blocks, the maximum fell from 47 to 12 blocks,
and isolated peaks fell from one to zero while land/ocean coverage remained unchanged. Planning p95 remains below
the 150 ms prototype target.

Phase 2 remains open because the production JAR still bundles only the small Bergen fixture. Step 9B must package
meaningful Northern Europe coverage and add geographic regression outputs. Step 9C must add the initial diagnostic
commands and record successful graphical client save/reopen and dedicated-server stop/restart acceptance before
Phase 3 hydrology begins.
