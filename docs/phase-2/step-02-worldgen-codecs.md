# Phase 2 Step 2: world-generation codecs

This step establishes the Minecraft serialization and registry boundary for the Orbis Terrae biome source and
chunk generator. It deliberately does not create a dimension or generate placeholder terrain.

## Registered codec types

Both codec types are registered under `orbis_terrae:earth` in their separate vanilla registries:

| Registry | Java type | Serialized fields |
| --- | --- | --- |
| Biome source | `OrbisBiomeSource` | `biome` |
| Chunk generator | `OrbisChunkGenerator` | `biome_source`, `profile` |

`OrbisWorldgenRegistries` owns the deferred registers and attaches them to the common mod event bus. The
common registration path contains no client-only classes and remains dedicated-server safe.

## Biome-source contract

`OrbisBiomeSource` currently returns one explicitly serialized biome for every quart coordinate. This is a
minimal codec boundary rather than the final geographic biome model. Step 3 will use it when adding the
first Orbis Terrae dimension and minimal land/ocean biome selection.

The selected biome is stored through Minecraft's holder codec, so datapack registry references remain part of
normal world-generation serialization rather than being converted into ad-hoc strings.

## Chunk-generator contract

`OrbisChunkGenerator` serializes:

- its complete `BiomeSource` through Minecraft's polymorphic biome-source codec;
- one built-in immutable world-profile ID.

Construction resolves the profile ID through `WorldProfiles.require`, rejecting unknown profiles before any
world generation starts. Dimension height, minimum Y, and sea level already come from the resolved profile.

Terrain methods intentionally throw a clear `UnsupportedOperationException`. This prevents an accidentally
referenced Step 2 generator from creating a misleading empty or placeholder world. Steps 4 and 5 will replace
that guard with atlas sampling and deterministic terrain-column filling.

## Validation

`Phase2CodecRegistrationTest` locks down:

- both `orbis_terrae:earth` registry IDs;
- the deferred-holder IDs before registry events fire;
- the biome-source serialized field name;
- the chunk-generator serialized field names.

The focused commands are:

```bash
./gradlew phase2CodecCheck --no-configuration-cache --warning-mode=fail
./gradlew phase2Check --no-configuration-cache --warning-mode=fail
```

The complete pre-merge gate remains:

```bash
./gradlew clean allChecks --no-configuration-cache --warning-mode=fail
```

## Deferred to Step 3

Step 3 adds the Orbis Terrae dimension data, creates the first minimal biome selection, and proves that
Minecraft can decode the registered types from world-generation JSON. Terrain remains deferred until atlas
sampling is connected in Step 4.
