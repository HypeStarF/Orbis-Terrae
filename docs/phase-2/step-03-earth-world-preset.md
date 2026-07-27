# Phase 2 Step 3: Earth world preset

This step makes the registered `orbis_terrae:earth` codecs available through Minecraft's normal world-creation
pipeline. It adds data-driven dimension settings and an Earth world preset without implementing terrain columns
before the atlas sampling pipeline is ready.

## Earth as the overworld stem

The `orbis_terrae:earth` world preset installs the Orbis Terrae dimension type and chunk generator under the
`minecraft:overworld` stem. This is intentional:

- players spawn in Earth without a bootstrap dimension or portal;
- vanilla save, respawn, map, and command semantics continue to treat Earth as the primary world;
- the Nether and End remain the standard vanilla stems;
- later terrain work can focus on the generator instead of cross-dimension player transfer.

The preset is added to `#minecraft:normal` and is named `Orbis Terrae` through the
`generator.orbis_terrae.earth` translation key.

## Dimension type

`data/orbis_terrae/dimension_type/earth.json` uses overworld behavior with the Phase 2 profile bounds:

| Setting | Value |
| --- | ---: |
| Minimum Y | `-64` |
| Height | `384` |
| Logical height | `384` |
| Coordinate scale | `1.0` |
| Effects | `minecraft:overworld` |

Beds, skylight, raids, and natural overworld behavior remain enabled. Nether-only behavior such as ultrawarm
physics, piglin safety, and respawn anchors remains disabled.

## Minimal biome selection

The preset configures `OrbisBiomeSource` with `minecraft:plains` for every coordinate. This is a deliberate
bootstrap value, not a geographic claim. Step 4 will sample the atlas land mask and elevation layers; biome
classification can then distinguish land and ocean using real atlas data instead of an invented placeholder map.

## Verification

Fast resource contracts:

```bash
./gradlew phase2WorldPresetCheck --no-configuration-cache --warning-mode=fail
```

Headless Minecraft registry and codec loading:

```bash
./gradlew phase2WorldgenSmoke --no-configuration-cache --warning-mode=fail
```

Complete Phase 2 verification:

```bash
./gradlew phase2Check --no-configuration-cache --warning-mode=fail
```

`allChecks` also includes the headless worldgen smoke. The focused preset check is preferred while editing JSON;
the smoke is required before merging because it proves NeoForge can register the codecs and decode the dynamic
worldgen resources together.

## Deferred to Step 4

The custom chunk generator still fails clearly when terrain methods are invoked. This preset is therefore a
registry and world-creation integration milestone, not yet a playable terrain release. Step 4 adds deterministic
atlas elevation and land-mask sampling at Minecraft block coordinates.
