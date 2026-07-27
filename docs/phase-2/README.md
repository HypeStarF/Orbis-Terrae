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
8. Benchmark the first terrain pipeline and perform the Phase 2 exit audit

## Exit criteria

Phase 2 closes only when:

- the game creates an Orbis Terrae world;
- Scandinavia has recognisable large-scale land and sea terrain;
- the same seed and immutable manifest reproduce identical chunks;
- singleplayer and dedicated server both work;
- no runtime internet access is required.

Steps 1 through 7 now establish immutable configuration, Minecraft serialization, data-driven world creation,
offline atlas installation, deterministic terrain columns, generator-level artificial-structure suppression,
bounded geographic spawn selection, stable chunk fingerprints, strict manifest persistence, and repeatable client
and dedicated-server launch preparation. Step 8 benchmarks the terrain pipeline and performs the Phase 2 exit audit.
