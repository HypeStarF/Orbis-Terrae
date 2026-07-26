# Orbis Terrae — Master Project Plan

**Document version:** 1.0
**Project status:** Approved for development
**Target platform:** Minecraft Java Edition 1.21.1
**Mod loader:** NeoForge 21.1.244
**Java version:** Java 21
**Initial development region:** Northern Europe, primarily Scandinavia
**Initial compatibility examples:** Mekanism and Immersive Engineering

---

## 1. Project identity

| Field                        | Value                                  |
| ---------------------------- | -------------------------------------- |
| Project name                 | Orbis Terrae                           |
| Mod name                     | Orbis Terrae                           |
| Mod ID                       | `orbis_terrae`                         |
| Group ID                     | `me.sdmannen`                          |
| Artifact ID                  | `orbis-terrae`                         |
| Main class                   | `me.sdmannen.orbis_terrae.OrbisTerrae` |
| Minecraft version            | `1.21.1`                               |
| NeoForge version             | `21.1.244`                             |
| Initial release type         | Private development build              |
| Possible future model        | Open-source project                    |
| Supported environments       | Singleplayer and dedicated servers     |
| Runtime internet requirement | None                                   |

Minecraft 1.20.5 and newer use Java 21, so the development environment, build server and dedicated server should all use a 64-bit Java 21 JDK or runtime.

---

# 2. Executive summary

Orbis Terrae will replace normal Overworld generation with a deterministic, configurable recreation of a natural Earth.

The generated world will represent:

* Real continents and coastlines
* Recognisable terrain and elevation
* Rivers, lakes, wetlands and drainage systems
* Ocean-floor terrain and marine regions
* Climate-derived biomes
* Reconstructed natural vegetation
* Geological provinces and plausible mineral deposits
* Regional cave systems
* Soil conditions
* Climate-sensitive agriculture
* Regional seasons
* Local solar time and latitude-dependent daylight
* Local weather and configurable severe events
* Regional wildlife spawning
* Optional wildfires
* User-selected geographic spawn areas

Human-made features will not generate. This includes cities, roads, villages, mines, monuments, temples, shipwrecks, strongholds and other artificial structures.

The world will support configurable horizontal scales from compact global maps to a horizontally 1:1 Earth. Vertical scaling will be configured independently and may use nonlinear compression. Chunk generation will be lazy by default, while regional and full pregeneration remain available as explicit options.

The mod will use an offline, preprocessed Earth atlas. Geographic processing will happen outside the Minecraft runtime wherever possible. The server will generate all authoritative terrain and simulation state, while a required client installation will render local sunlight, weather, seasons and softened longitude wrapping.

---

# 3. Honest feasibility assessment

The complete vision is a very large project.

For one developer with some Java and Minecraft experience but no GIS experience, Orbis Terrae should be treated as a series of progressively useful products rather than one enormous release.

Approximate planning scale:

| Deliverable                                        |    Rough solo effort |
| -------------------------------------------------- | -------------------: |
| Basic global Earth terrain prototype               |    4–8 person-months |
| Strong Scandinavian terrain prototype              |   8–14 person-months |
| Complete Scandinavian environmental vertical slice |  15–30 person-months |
| Broad global feature-complete release              | 30–60+ person-months |

These are planning estimates rather than promises. GIS learning, rendering modifications, hydrology and performance work create substantial uncertainty.

The project is realistic if it follows these rules:

1. Build one system at a time.
2. Prove each system in Scandinavia before expanding it globally.
3. Never process raw GIS datasets during normal gameplay.
4. Do not attempt seamless planetary wrapping in the first terrain prototype.
5. Do not build seasons, agriculture, animals and weather before terrain generation is stable.
6. Keep natural wonders and full ecosystem simulation outside the initial scope.
7. Release useful intermediate versions rather than waiting for the complete vision.

---

# 4. Product vision

## 4.1 Core promise

> Orbis Terrae generates a configurable, human-free Earth whose major geography is recognisable and geographically aligned, while reconstructing small-scale terrain, vegetation, caves and mineral deposits procedurally.

The project does not promise that every tree, cave, river bend or ore vein matches reality exactly.

## 4.2 Accuracy priorities

When geographic detail must be simplified, preserve features in this order:

1. Coastlines and major landforms
2. Major rivers and lakes
3. Climate and biome identity
4. Geological regions
5. Soil and vegetation
6. Local procedural detail

## 4.3 Product principles

### Deterministic

The same:

* Atlas version
* World profile
* Seed
* Compatibility mapping
* Generator version

must produce the same world.

### Offline

After installation, neither clients nor dedicated servers should require cloud services or internet access to generate terrain.

### Data-driven

Crops, wildlife, resources and compatibility mappings should be defined through data files wherever practical.

### Progressive fidelity

The mod must support a low-detail global atlas and higher-detail regional atlas layers.

### Immutable worlds

Fundamental geographic settings are locked when the world is created.

### Graceful fallback

Missing optional mods must produce Orbis Terrae placeholders rather than missing blocks or broken worlds.

### Safe defaults

Expensive features such as full pregeneration, extreme weather and wildfires should require deliberate activation.

### Server authoritative

Terrain, agriculture, resource state, weather effects and time simulation are controlled by the logical server.

---

# 5. Project scope

## 5.1 Required long-term systems

* Custom Earth coordinate system
* Equirectangular projection
* Configurable metres per block
* Configurable vertical transformation
* Linear and nonlinear vertical profiles
* Compressed polar caps
* East–west longitude wrapping
* Cartographic generalisation
* Deterministic chunk generation
* Real coastlines and terrain
* Global hydrology
* Ocean bathymetry
* Climate-derived biomes
* Ecotones
* Reconstructed natural vegetation
* Geological provinces
* Deposit-specific resource generation
* Regional caves and aquifers
* Soil properties
* Agriculture simulation
* Local seasons
* Opposite hemispheric seasons
* Wet and dry seasonal patterns
* Local solar time
* Latitude-dependent daylight
* Local weather
* Configurable severe weather
* Habitat-sensitive animals
* Carrying-cap approximations
* Optional wildfires
* Geographic spawn profiles
* Lazy, regional and total pregeneration
* Placeholder resources
* Data-driven compatibility
* World migration tools

## 5.2 Explicit non-goals for initial development

* Cities or roads
* Historical settlements
* Natural wonders
* Plate tectonics
* Long-term erosion
* Glacial movement
* Full ocean-current fluid simulation
* Full predator–prey ecosystems
* Animal migration
* Selective crop breeding
* Dynamically changing major river levels
* Unlimited vertical height
* Complete cross-seam redstone and fluid simulation
* Nether generation changes
* End generation changes
* Multi-loader support
* Multiple Minecraft versions

The Nether and End are outside Orbis Terrae’s responsibility. No compatibility or progression guarantees will initially be made for those dimensions.

---

# 6. Technical architecture

## 6.1 High-level system

```text
External geographic datasets
             │
             ▼
Orbis Terrae Atlas Compiler
             │
             ▼
Versioned offline atlas
             │
             ├── Global low-detail layers
             └── Northern Europe detail layers
             │
             ▼
World profile validation
             │
             ▼
Immutable world manifest
             │
             ▼
Server-side Earth generator
             │
             ├── Terrain and biomes
             ├── Hydrology and oceans
             ├── Geology and resources
             ├── Soil and agriculture
             ├── Weather and seasons
             └── Wildlife
             │
             ▼
Client synchronisation
             │
             ├── Local sky
             ├── Local precipitation
             ├── Seasonal visuals
             └── Wrapping transition
```

## 6.2 Core runtime technology

Orbis Terrae should register a custom `ChunkGenerator` and custom `BiomeSource`. Minecraft 1.21.1 exposes registries for both generator codecs, and `ChunkGenerator` provides the generation hooks needed for biome creation, terrain filling, surface construction, carving and feature placement.

The generator should not attempt to force Earth terrain through the vanilla noise router alone. It should directly calculate the intended terrain columns while reusing selected vanilla systems where they remain useful.

## 6.3 Main architectural layers

### Atlas layer

Reads geographic data from optimized binary tiles.

### Geographic layer

Converts Minecraft coordinates to latitude and longitude and applies wrapping, polar compression and scale rules.

### Generation layer

Creates terrain, oceans, surfaces, caves, vegetation and deposits.

### Simulation layer

Controls time, weather, agriculture, soil changes, fires and wildlife limits.

### Compatibility layer

Resolves abstract Orbis Terrae resources, crops, animals, blocks and fluids to installed mods.

### Client layer

Renders local daylight, weather, seasons and wrapping effects.

### Management layer

Handles profiles, manifests, pregeneration, commands, validation and migration.

---

# 7. Repository architecture

A multi-module Gradle repository is recommended.

```text
orbis-terrae/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
│
├── modules/
│   ├── minecraft-mod/
│   ├── atlas-api/
│   ├── atlas-compiler/
│   ├── compatibility-api/
│   ├── compatibility-mekanism/
│   ├── compatibility-immersive-engineering/
│   └── test-support/
│
├── atlas/
│   ├── schemas/
│   ├── profiles/
│   ├── provenance/
│   └── test-fixtures/
│
├── docs/
│   ├── architecture/
│   ├── datasets/
│   ├── configuration/
│   ├── compatibility/
│   └── decisions/
│
├── scripts/
└── README.md
```

## 7.1 Modules

### `minecraft-mod`

The actual NeoForge mod.

### `atlas-api`

A pure Java library for:

* Atlas manifests
* Tile reading
* Coordinate sampling
* Layer definitions
* Caching
* Checksums

It must not depend on Minecraft classes.

### `atlas-compiler`

Command-line GIS preprocessing application.

### `compatibility-api`

Shared interfaces and data schemas for integrations.

### Official compatibility modules

Separate integrations for:

* Mekanism
* Immersive Engineering

Keeping these separate prevents the core project from directly depending on either mod.

### `test-support`

Reusable map fixtures, deterministic test worlds and benchmark helpers.

---

# 8. Java package structure

```text
me.sdmannen.orbis_terrae
├── OrbisTerrae
├── registry
├── config
├── profile
├── manifest
├── atlas
├── geo
│   ├── projection
│   ├── scale
│   └── generalization
├── worldgen
│   ├── chunk
│   ├── biome
│   ├── surface
│   ├── vegetation
│   └── ocean
├── hydrology
├── geology
│   ├── cave
│   ├── deposit
│   └── reservoir
├── climate
├── season
├── time
├── weather
├── soil
├── agriculture
├── fauna
├── wildfire
├── spawn
├── compatibility
├── network
├── command
├── pregen
├── migration
├── client
│   ├── sky
│   ├── weather
│   ├── season
│   └── wrapping
└── debug
```

Packages should depend inward toward stable abstractions. For example, agriculture may depend on soil and climate interfaces, but soil must not depend on agriculture.

---

# 9. Configuration architecture

## 9.1 Installation configuration

```text
config/orbis-terrae-common.toml
```

Contains mutable installation settings such as:

* Cache memory
* Generation threads
* Debug logging
* Client rendering quality
* Pregeneration throttling
* Default profile
* Atlas path

## 9.2 World profiles

```text
config/orbis_terrae/profiles/
├── global-compact.toml
├── global-survival.toml
├── detailed-earth.toml
└── custom.toml
```

Profiles are editable before world creation.

## 9.3 Immutable manifest

```text
world/orbis_terrae/manifest.json
```

The manifest stores:

* Generator version
* Atlas version
* Profile schema version
* Configuration hash
* Seed
* Scale
* Projection
* Vertical settings
* Resource mappings
* Installed compatibility packs
* Dataset provenance
* World creation date
* Migration history

Level-wide persistent state can use Minecraft’s `SavedData` system, which is intended for additional data associated with a level.

## 9.4 Datapacks

JSON datapacks define:

* Crops
* Wildlife categories
* Resource substitutions
* Soil interactions
* Greenhouse blocks
* Artificial lighting
* Fertilisers
* Irrigation systems
* Severe weather definitions

NeoForge custom datapack registries use codecs to load data-defined entries. Data maps can attach additional data to registered objects, but because they are reloadable, immutable world-generation choices must be copied into the world manifest instead of changing silently during `/reload`.

## 9.5 Graphical setup wizard

The GUI should provide:

1. Preset selection
2. Scale settings
3. Vertical settings
4. Environmental options
5. Compatibility detection
6. Spawn selection
7. Pregeneration settings
8. Storage and time estimates
9. Validation report
10. Final confirmation

The GUI edits a profile. It does not replace the underlying profile format.

---

# 10. Default geographic profiles

Exact values must be confirmed through testing.

## 10.1 Global Compact

**Purpose:** Small friend servers and fast global travel.

Provisional profile:

```toml
[scale]
horizontal_metres_per_block = 2000

[vertical]
mode = "nonlinear"
profile = "compact"

[generalization]
preserve_major_rivers = true
preserve_major_islands = true
preserve_strategic_straits = true
```

Approximate projected size:

* 20,000 blocks east–west
* 10,000 blocks north–south

## 10.2 Global Survival

**Purpose:** Main recommended global experience.

```toml
[scale]
horizontal_metres_per_block = 1000

[vertical]
mode = "nonlinear"
profile = "global_survival"
```

Approximate projected size:

* 40,000 blocks east–west
* 20,000 blocks north–south

Earth’s equatorial circumference is about 40,000 kilometres, so these dimensions follow directly from the selected metres-per-block value.

## 10.3 Detailed Earth

Provisional value:

```toml
horizontal_metres_per_block = 100
```

This is intended for regional play and powerful servers rather than complete global pregeneration.

## 10.4 Horizontally 1:1

```toml
horizontal_metres_per_block = 1
```

The option remains available.

Restrictions:

* Lazy generation strongly recommended
* Full pregeneration marked as practically infeasible
* Atlas detail may be lower than one metre in many places
* Vertical scale remains independent
* Normal travel becomes extremely slow
* Large coordinate arithmetic must be tested carefully

---

# 11. Atlas system

## 11.1 Atlas philosophy

The runtime mod must not include GIS libraries or parse giant GeoTIFF files.

The atlas compiler converts source datasets into a purpose-built format optimized for:

* Random tile access
* Multiresolution sampling
* Small memory use
* Deterministic output
* Versioning
* Offline distribution

## 11.2 Candidate source layers

Every dataset must pass a redistribution and attribution review before it is bundled.

| Layer                   | Initial candidate                | Use                                   |
| ----------------------- | -------------------------------- | ------------------------------------- |
| Elevation               | Copernicus DEM                   | Land elevation                        |
| Land cover              | ESA WorldCover                   | Existing cover and farmland detection |
| Hydrology               | HydroSHEDS family                | Rivers, watersheds and lakes          |
| Climate                 | WorldClim                        | Monthly climate baselines             |
| Soil                    | SoilGrids                        | Soil properties and depth             |
| Global bathymetry       | GEBCO                            | Ocean floor                           |
| European bathymetry     | EMODnet                          | Detailed Scandinavian seas            |
| European geology        | EGDI                             | Geological units                      |
| Global geology fallback | USGS world geology               | Coarse global geology                 |
| Mineral occurrences     | Geological surveys and USGS data | Resource-province guidance            |

Copernicus DEM offers global elevation products at approximately 30- and 90-metre resolution, while ESA WorldCover provides global 10-metre land-cover products. Copernicus DEM is a surface model rather than a pure bare-earth terrain model, so preprocessing must reduce vegetation and remaining human-made elevation artefacts.

HydroSHEDS provides catchments, river networks, lakes and other hydrographic products at multiple scales. WorldClim provides global monthly climate layers, while SoilGrids provides global soil-property predictions at 250-metre resolution and several depth intervals.

GEBCO provides a global 15-arc-second bathymetric grid. For the Northern Europe prototype, EMODnet’s European bathymetry should be evaluated as a higher-detail override. EGDI should be evaluated for European geological units and national geological services used where their licensing permits redistribution.

## 11.3 Human-free vegetation reconstruction

Modern farmland should not simply remain agricultural land.

The compiler should:

1. Identify farmland and built-up classes.
2. Remove artificial land-cover classifications.
3. Sample nearby natural vegetation.
4. Calculate potential vegetation from:

   * Temperature
   * Rainfall
   * Rainfall seasonality
   * Elevation
   * Soil
   * Slope
   * Wetness
   * Distance from ocean
5. Fill removed areas with plausible native vegetation.
6. Smooth the result into surrounding ecotones.

This is a reconstruction, not a historical claim about an exact pre-agricultural year.

## 11.4 Atlas hierarchy

```text
orbis-terrae-atlas/
├── atlas-manifest.json
├── layers/
│   ├── elevation/
│   ├── bathymetry/
│   ├── land-mask/
│   ├── hydrology/
│   ├── climate/
│   ├── soil/
│   ├── vegetation/
│   ├── geology/
│   ├── resource-provinces/
│   └── fauna-regions/
├── dictionaries/
├── provenance/
└── checksums/
```

## 11.5 Tiling

Initial design:

* Projection: EPSG:4326-like equirectangular grid
* Tile size: 256 × 256 samples
* Multiresolution pyramid
* Independent layer resolutions
* Per-tile checksum
* Per-layer schema version
* Compression chosen after benchmarking

The implementation should support a global base layer and regional override layers.

## 11.6 Suggested data encodings

| Layer                 | Encoding                                   |
| --------------------- | ------------------------------------------ |
| Elevation             | Signed 16-bit or delta-compressed integers |
| Bathymetry            | Signed 16-bit integers                     |
| Land mask             | Bitset                                     |
| Biome inputs          | Quantized bytes or shorts                  |
| Monthly temperature   | Twelve quantized signed values             |
| Monthly precipitation | Twelve quantized unsigned values           |
| Soil properties       | Quantized property arrays                  |
| Vegetation            | Categorical byte or short                  |
| Geology               | Categorical integer IDs                    |
| Hydrology             | Topological vector segments                |
| Resource provinces    | Polygon IDs and procedural parameters      |
| Fauna regions         | Categorical habitat IDs                    |

## 11.7 Runtime cache

The atlas reader should include:

* Memory-bounded least-recently-used cache
* Read-ahead for nearby chunks
* Concurrent safe tile loading
* Per-layer cache statistics
* Corrupt tile detection
* Optional disk cache for derived tiles

No atlas file should be loaded in its entirety.

---

# 12. Coordinate system and projection

## 12.1 Basic mapping

```text
Minecraft X → longitude
Minecraft Z → latitude
Minecraft Y → transformed elevation
```

The map should be centred so that the 1:1 projected Earth remains inside Minecraft’s usable coordinate range. This must be verified against the exact 1.21.1 engine constants during Phase 1.

## 12.2 Longitude wrapping

Logical coordinate normalization:

```text
wrappedX = floorMod(x - minimumX, worldWidth) + minimumX
```

All atlas sampling uses wrapped coordinates from the first prototype.

Initial crossing behaviour:

1. Preload destination chunks.
2. Fade or briefly mask the transition.
3. Transfer the player to the corresponding opposite-edge coordinate.
4. Preserve velocity and orientation where safe.
5. Correct attached entities and vehicles.

Later development may render opposite-edge terrain before crossing.

## 12.3 Polar caps

Equirectangular distortion becomes extreme near the poles.

The selected design is:

* Normal equirectangular mapping through most latitudes
* Gradual longitudinal compression near the poles
* A finite polar-cap region
* Special pole-crossing behaviour
* No infinite line representing a single geographic pole

Exact polar compression must be visually tested.

---

# 13. Cartographic generalisation

Every preset must define a geographic generalisation profile.

## 13.1 Required operations

* Coastline simplification
* Island preservation
* Narrow strait preservation
* Lake enlargement
* River-width exaggeration
* River-length simplification
* Mountain prominence preservation
* Wetland aggregation
* Minimum biome size
* Minimum habitat size

## 13.2 Feature importance

Features should carry priority values.

Examples:

* Major continental coastline: critical
* Baltic Sea: critical
* Strait of Gibraltar: critical
* Major lake: high
* Small unnamed lake: low
* Danube or Rhine: high
* Temporary stream: low

## 13.3 Scale-aware output

A feature that is smaller than one block can still appear when:

* It has high geographic importance.
* Removing it would disconnect a sea.
* Removing it would destroy regional identity.
* It is part of the main drainage network.

---

# 14. Vertical transformation

## 14.1 Requirements

Vertical generation must separately control:

* Lowlands
* Hills
* Mountains
* Ocean depth
* River valleys
* Cave reserve
* Glacial terrain
* Maximum terrain height

## 14.2 Supported modes

### Linear

```text
minecraftHeight = realElevation × multiplier
```

Suitable primarily for custom experimentation.

### Piecewise nonlinear

Recommended default:

```text
Low elevations      → modest exaggeration
Hills               → medium exaggeration
Mountain elevations → stronger visual prominence
Extreme elevations  → compressed toward build limit
```

### Curve-based

Advanced profiles may define control points:

```toml
[[vertical.control_point]]
real_metres = 0
blocks = 0

[[vertical.control_point]]
real_metres = 500
blocks = 90

[[vertical.control_point]]
real_metres = 2000
blocks = 400

[[vertical.control_point]]
real_metres = 9000
blocks = 1300
```

## 14.3 Height policy

The initial project will use an expanded but finite custom dimension. It will not attempt to remove Minecraft’s engine height limits.

The exact safe dimension height must be benchmarked before the main profiles are finalized. More vertical sections increase lighting, generation and memory costs even when most blocks are air.

---

# 15. Chunk-generation pipeline

Each subsystem receives a separate deterministic random stream derived from:

* World seed
* Chunk coordinate
* Subsystem identifier

This prevents changes to tree placement from changing caves or mineral deposits.

## 15.1 Per-chunk sequence

1. Normalize wrapped coordinates.
2. Convert block positions to geographic coordinates.
3. Select atlas resolution for the world profile.
4. Load required atlas tiles.
5. Sample land mask and elevation.
6. Apply vertical transformation.
7. Apply hydrological terrain corrections.
8. Generate ocean and lake basins.
9. Generate geological strata.
10. Generate caves and aquifers.
11. Create surface and soil layers.
12. Assign climate-derived biomes.
13. Apply ecotone blending.
14. Place vegetation.
15. Generate solid mineral deposits.
16. Register fluid and gas reservoirs.
17. Apply river, wetland and coastal features.
18. Record habitat and agricultural metadata.
19. Run continuity checks.
20. Finalize heightmaps and lighting inputs.

## 15.2 Structure policy

The Orbis Terrae dimension disables artificial structures, including:

* Villages
* Mineshafts
* Strongholds
* Trial chambers
* Ancient cities
* Ocean monuments
* Shipwrecks
* Ruined portals
* Temples
* Pillager outposts
* Trail ruins
* Igloos
* Woodland mansions

Natural features may still use Minecraft’s internal placement mechanisms where useful, but they must not appear as artificial constructions.

---

# 16. Terrain and surface generation

## 16.1 Terrain responsibilities

* Preserve major landforms.
* Avoid visible tile and chunk seams.
* Reconstruct local detail below atlas resolution.
* Preserve slopes and drainage directions.
* Prevent floating coastlines.
* Preserve recognisable Scandinavian fjords.
* Avoid excessive roughness in plains.

## 16.2 Procedural detail

Atlas elevation defines low-frequency terrain.

Procedural noise adds:

* Small ridges
* Rock outcrops
* Local valleys
* Shore detail
* Dunes
* Moraine-like terrain
* Cliff variation

Procedural variation must be constrained so that it does not move major rivers or erase real terrain.

---

# 17. Hydrology

## 17.1 Global network

Rivers must be represented as a connected topological network rather than independent chunk features.

Each river segment should know:

* Upstream segment
* Downstream segment
* Catchment size
* Relative flow
* Width category
* Source type
* Mouth type
* Lake connections
* Geographic priority

## 17.2 Generation rules

* Rivers cannot flow uphill.
* Tributaries must connect.
* Lakes must have valid outlets unless endorheic.
* River mouths must meet the sea.
* Deltas should be generated at suitable major mouths.
* Waterfalls should follow elevation discontinuities.
* River width must respect scale-profile minimums.

## 17.3 Initial seasonal behaviour

Major river levels remain visually static in the first implementation.

Seasonal state may affect:

* Freezing
* Nearby soil moisture
* Crop conditions
* Snow cover
* Wetland appearance

Dynamic river levels remain postponed.

---

# 18. Oceans and coasts

## 18.1 Ocean features

* Continental shelves
* Continental slopes
* Abyssal plains
* Trenches
* Underwater ridges
* Seamounts
* Warm and cold marine biomes
* Kelp regions
* Coral suitability
* Polar seas
* Sea ice
* Coastal wetlands
* Estuaries
* Fjords

## 18.2 Separate depth transformation

Ocean depth uses a separate curve from land elevation.

The system must reserve enough vertical space for:

* Seabed
* Sediment layers
* Marine caves
* Aquifers
* Geological resources

## 18.3 Scandinavia priorities

The first regional atlas must particularly test:

* Norwegian fjords
* North Sea shelf
* Baltic Sea shallowness
* Kattegat and Skagerrak
* Icelandic shelf
* Arctic waters
* Archipelagos

---

# 19. Geology and caves

## 19.1 Geological model

Each position should have:

* Geological province
* Primary lithology
* Geological age category
* Structural setting
* Deposit suitability
* Cave suitability
* Aquifer properties
* Geothermal potential

## 19.2 Cave categories

* Limestone karst
* Lava tubes
* Volcanic chambers
* Fracture caves
* Coastal caves
* Glacial caves
* Generic erosional caves
* Underground rivers
* Aquifers

## 19.3 Generation strategy

The atlas establishes broad geological conditions. Procedural algorithms generate exact underground forms.

Vanilla noise caves may be retained only as a controlled fallback. They should not appear at identical frequency beneath every geological province.

---

# 20. Resources and deposits

## 20.1 Abstract resource registry

Examples:

```text
orbis_terrae:iron
orbis_terrae:copper
orbis_terrae:tin
orbis_terrae:bauxite
orbis_terrae:uranium
orbis_terrae:rare_earths
orbis_terrae:coal
orbis_terrae:crude_oil
orbis_terrae:natural_gas
orbis_terrae:groundwater
orbis_terrae:geothermal
```

## 20.2 Deposit generators

| Deposit     | Shape                               |
| ----------- | ----------------------------------- |
| Coal        | Layered seams                       |
| Iron        | Large formations or lenses          |
| Copper      | Belts, veins or disseminated bodies |
| Gold        | Veins and placer deposits           |
| Diamonds    | Kimberlite-like pipes               |
| Salt        | Sedimentary layers and domes        |
| Bauxite     | Weathered surface zones             |
| Rare earths | Intrusions or mineralized zones     |
| Oil         | Sedimentary reservoirs              |
| Natural gas | Reservoir metadata                  |
| Groundwater | Aquifer metadata                    |
| Geothermal  | Heat and permeability fields        |

## 20.3 Placeholder policy

Solid minerals generate as Orbis Terrae blocks when no compatible mod mapping exists.

Oil, gas, groundwater and geothermal resources use:

* Reservoir metadata
* Optional placeholder fluids
* Optional bearing-rock blocks
* Surveyable quantity and depth

## 20.4 Mapping priority

1. World-locked explicit user mapping
2. Official compatibility pack
3. Community compatibility pack
4. Default Orbis Terrae placeholder

## 20.5 Migration

A migration tool may intentionally convert placeholders to newly installed mod blocks.

It must:

* Create a backup
* Show the affected resource mappings
* Process selected regions
* Support pause and resume
* Record the migration in the manifest
* Never silently reinterpret an existing block ID

---

# 21. Soil system

## 21.1 Hybrid representation

Broad soil categories may be visible through different blocks, while detailed values remain stored as environmental data.

## 21.2 Soil properties

* Fertility
* Drainage
* Acidity
* Moisture retention
* Salinity
* Depth
* Organic content
* Sand fraction
* Silt fraction
* Clay fraction
* Coarse fragments
* Compaction
* Local water availability

## 21.3 Soil state

Base soil comes from the atlas.

Player actions may modify:

* Fertility
* Moisture
* Salinity
* Compaction
* Organic matter
* Soil category

Only modified soil requires persistent per-chunk state. Unmodified values can always be reconstructed from the atlas and seed.

---

# 22. Agriculture

## 22.1 Crop suitability model

```text
Temperature suitability
× soil suitability
× moisture suitability
× seasonal suitability
× light suitability
× nutrient suitability
= growth rate and yield
```

## 22.2 Consequences

The selected default is simulation mode:

* Severely unsuitable crops may fail.
* Marginal crops grow slowly and yield less.
* Suitable crops perform well.
* Extreme cold, heat or drought can damage crops.

## 22.3 Player mitigation

Players can overcome environmental limits through:

* Irrigation
* Fertiliser
* Artificial lighting
* Heating
* Greenhouses
* Soil replacement
* Drainage
* Salinity control

Selective breeding remains the responsibility of another mod and may receive compatibility later.

## 22.4 Greenhouses

The initial greenhouse system should avoid scanning huge structures every tick.

Recommended approach:

* Detect a bounded enclosure.
* Cache its boundary.
* Recalculate only after relevant block changes.
* Recognize configurable transparent roof blocks.
* Calculate heating and lighting inputs.
* Apply an internal climate modifier.

---

# 23. Climate and biomes

## 23.1 Biome inputs

* Monthly temperature
* Monthly precipitation
* Rainfall seasonality
* Latitude
* Elevation
* Ocean distance
* Prevailing wind
* Mountain barriers
* Soil
* Slope
* Groundwater
* Wetlands

## 23.2 Biome output

Orbis Terrae may use:

* Vanilla biomes
* Orbis Terrae biomes
* Mapped modded biomes
* Transitional ecotones

## 23.3 Ecotones

Biome boundaries should be blended through transition zones, such as:

* Tundra to taiga
* Taiga to temperate forest
* Forest to grassland
* Grassland to semi-arid terrain
* Wetland to forest
* Alpine to subalpine vegetation

## 23.4 Seasonal state

Biome identity remains stable, but local appearance and behaviour may change with season.

---

# 24. Time, daylight and seasons

## 24.1 Global calendar

The server maintains one global astronomical calendar.

Configurable parameters:

* Year length
* Day length
* Axial tilt
* Starting date
* Time acceleration
* Seasonal intensity

## 24.2 Local solar time

Local time is derived from longitude.

The client renders:

* Sun position
* Moon position
* Dawn and dusk
* Stars
* Local brightness
* Seasonal sunrise and sunset direction

## 24.3 Latitude-dependent daylight

The system should support:

* Longer summer days at high latitudes
* Shorter winter days at high latitudes
* Polar day
* Polar night
* Lower seasonal variation near the equator

## 24.4 Hemispheres

Northern and Southern Hemisphere seasons are offset.

Equatorial areas may use:

* Wet season
* Dry season
* Monsoon cycles

rather than a strong four-season cycle.

## 24.5 Beds

Beds:

* Set spawn
* Provide a configurable health or rest benefit
* Do not skip time

---

# 25. Weather

## 25.1 Weather model

Use simplified moving weather cells rather than scientific atmospheric simulation.

Each cell tracks:

* Temperature anomaly
* Humidity
* Pressure category
* Wind
* Cloud cover
* Precipitation
* Storm intensity
* Snow conditions
* Drought state
* Fire risk

## 25.2 Influences

* Baseline climate
* Season
* Latitude
* Elevation
* Ocean proximity
* Prevailing wind
* Rain shadows
* Nearby water temperature category
* Existing weather cells

## 25.3 Performance model

The server must not continuously simulate every weather cell on Earth.

Instead:

* Active cells near loaded regions receive detailed simulation.
* Distant cells use deterministic coarse progression.
* Inactive cells are reconstructed from time and seed.
* Only exceptional state changes are persisted.

## 25.4 Severe events

Configurable events may include:

* Thunderstorms
* Blizzards
* Cold waves
* Heatwaves
* Droughts
* Sandstorms
* Tropical storms
* Tropical cyclones

Tornadoes should be postponed until the ordinary weather system is stable.

---

# 26. Wildfires

## 26.1 Ignition

Possible causes:

* Lightning
* Player fire
* Lava
* Configured severe heat events

## 26.2 Spread factors

* Vegetation
* Fuel moisture
* Wind
* Temperature
* Drought
* Recent rainfall
* Terrain slope

## 26.3 Safety rules

* Disabled by default during early releases
* Simulated only in loaded chunks
* Maximum active area
* Maximum simultaneous fires
* Server performance cutoff
* Automatic ecological recovery
* Configurable player-fire behaviour
* No unlimited fire simulation in unloaded terrain

---

# 27. Wildlife

## 27.1 Habitat categories

The atlas defines ecological categories rather than individual mod entities.

Examples:

```text
boreal_large_herbivore
boreal_predator
temperate_small_mammal
arctic_bird
savanna_large_grazer
tropical_predator
```

## 27.2 Compatibility mapping

A datapack maps categories to installed entities.

## 27.3 Spawn evaluation

Animal spawning considers:

* Habitat
* Temperature
* Season
* Vegetation
* Elevation
* Water access
* Local carrying capacity
* Existing population

## 27.4 Scope limits

The first system will not simulate:

* Food chains
* Predation populations
* Continental migration
* Genetic variation
* Extinction

---

# 28. Spawn selection

## 28.1 Supported modes

### Exact coordinate

```toml
mode = "coordinates"
latitude = 59.3293
longitude = 18.0686
```

### Named point

```toml
mode = "named"
location = "stockholm"
```

### Geographic region

```toml
mode = "region"
region = "northern_europe"
```

### Random allowed region

```toml
mode = "random_region"
allowed_regions = [
  "scandinavia",
  "western_europe",
  "southern_africa"
]
```

## 28.2 Safety resolver

The resolver checks:

* Solid terrain
* Slope
* Water depth
* Climate
* Available freshwater
* Vegetation
* Height
* Clearance
* Nearby hazards
* Configured biome restrictions

---

# 29. Client-server architecture

The client mod is required.

## 29.1 Server responsibilities

* Chunk generation
* Calendar
* Local environmental values
* Weather effects
* Agriculture
* Soil changes
* Resource state
* Wildlife limits
* Spawn logic
* Longitude transfer
* Validation

## 29.2 Client responsibilities

* Local sky rendering
* Local sunlight
* Local weather rendering
* Seasonal vegetation visuals
* Environmental interface
* Seam transition
* Optional map preview

## 29.3 Synchronised data

* Manifest hash
* Calendar state
* Local weather snapshot
* Local season state
* Local solar values
* Wrapping transition data
* Relevant environmental values

NeoForge networking uses registered custom payloads and stream codecs. Payload sizes are limited, so Orbis Terrae should send compact local snapshots rather than atlas tiles or whole weather grids.

## 29.4 Connection validation

When connecting, verify:

* Mod protocol version
* Atlas version
* Compatibility schema version
* Required datapack hashes
* Manifest compatibility

The server remains authoritative if client profile files differ.

---

# 30. Pregeneration

## 30.1 Modes

| Mode     | Behaviour                          |
| -------- | ---------------------------------- |
| Lazy     | Generate only loaded chunks        |
| Regional | Generate selected geographic areas |
| Full     | Attempt the entire projected Earth |

## 30.2 Estimator

Before starting, estimate:

* Projected block dimensions
* Chunk count
* Expected disk range
* Expected duration range
* Current free disk
* Memory recommendation
* Atlas-cache needs

Duration estimates should be based on a short benchmark generated on the user’s machine.

## 30.3 Confirmation rules

For high-cost operations:

1. Show warnings.
2. Require explicit confirmation.
3. Require an advanced override for extreme configurations.
4. Never describe 1:1 full pregeneration as practically achievable.

## 30.4 Job system

Pregeneration jobs must support:

* Pause
* Resume
* Cancellation
* Checkpointing
* Region priority
* Throttling
* Progress display
* Crash recovery
* Live-server TPS protection

## 30.5 Suggested commands

```text
/orbis pregen estimate
/orbis pregen start
/orbis pregen pause
/orbis pregen resume
/orbis pregen cancel
/orbis pregen status
```

---

# 31. Administrative and debugging commands

```text
/orbis info
/orbis profile
/orbis manifest
/orbis coords
/orbis locate <latitude> <longitude>
/orbis climate
/orbis soil
/orbis geology
/orbis resource
/orbis weather
/orbis season
/orbis atlas status
/orbis atlas verify
/orbis cache stats
/orbis debug chunk
/orbis spawn test
/orbis migrate
```

Commands exposing hidden deposits should require operator permissions or debug mode.

---

# 32. Compatibility system

## 32.1 Compatibility API

The public API should support:

* Resource mappings
* Crop profiles
* Entity habitat mappings
* Soil interactions
* Fluids
* Greenhouse blocks
* Irrigation devices
* Fertilisers
* Artificial lighting
* Heating systems
* Survey equipment

## 32.2 Conditional loading

Compatibility data should only load when required mods are installed. NeoForge data-loading conditions can be used to prevent incompatible JSON resources from loading.

## 32.3 Mekanism example

The Mekanism example should demonstrate:

* Mapping Orbis Terrae mineral types to registered Mekanism resources
* Tag and recipe integration
* Optional fluid or gas integration where the installed version supports it
* Survey or processing interoperability

Exact registry names must be verified against the selected Mekanism build rather than assumed.

## 32.4 Immersive Engineering example

The Immersive Engineering example should demonstrate:

* Metal resource mapping
* Mineral processing compatibility
* Fluid and reservoir integration where applicable
* Fertiliser, machinery or power interactions where supported

## 32.5 Packaging

Recommended artifacts:

```text
orbis-terrae.jar
orbis-terrae-compat-mekanism.jar
orbis-terrae-compat-immersive-engineering.jar
```

Community packs may be datapacks when Java hooks are unnecessary.

---

# 33. Performance targets

These are engineering targets, not guaranteed system requirements.

## 33.1 Reference environments

### Singleplayer target

* Modern six-core gaming CPU
* 16 GB system RAM
* SSD
* Default view distance around 8–10 chunks

### Dedicated-server target

* User’s Intel i7-8700 or comparable
* SSD storage
* 16–32 GB server RAM
* Up to 10 players
* View distance around 8
* Simulation distance around 6

## 33.2 Initial targets

* Maintain 20 TPS during ordinary ten-player exploration.
* Avoid sustained main-thread stalls above 50 ms.
* Warm atlas-tile lookup below approximately 2 ms at the 95th percentile.
* Average Global Survival chunk generation below approximately 150 ms CPU time per chunk after optimization.
* Keep default atlas and simulation overhead below approximately 2 GB RAM.
* Prevent pregeneration from reducing live-server TPS below a configured threshold.
* Avoid runtime internet access.
* Avoid sending atlas data over normal gameplay networking.

These values may be revised after the first benchmark suite.

---

# 34. Testing strategy

## 34.1 Unit tests

* Coordinate conversion
* Longitude wrapping
* Polar compression
* Vertical curves
* Atlas decoding
* Tile boundaries
* Seed derivation
* Resource mappings
* Climate interpolation
* Crop suitability

## 34.2 Determinism tests

Generate the same area repeatedly and compare:

* Block hashes
* Biome hashes
* Reservoir IDs
* Resource deposits
* Cave results
* Vegetation placement

## 34.3 Continuity tests

* Adjacent chunk terrain
* Rivers crossing chunks
* Coastlines
* Geological strata
* Ecotones
* Wrapping seam
* Atlas regional override boundaries

## 34.4 Geographic regression tests

Maintain reference outputs for locations such as:

* Southern Sweden
* Norwegian fjords
* Finnish lake districts
* Denmark
* Iceland
* Northern Norway
* Baltic coast
* Ural test area

## 34.5 GameTests

Use NeoForge/Minecraft GameTests for interactions such as:

* Crop growth
* Irrigation
* Greenhouses
* Fire spread
* Placeholder conversion
* Survey tools
* Spawn resolution

NeoForge provides a GameTest framework for registered test environments and test instances.

## 34.6 Performance tests

* Cold tile reads
* Warm tile reads
* Parallel generation
* Pregeneration throughput
* Ten-player exploration
* Weather-cell scaling
* Large-height lighting
* Memory pressure
* Long-running dedicated-server soak test

## 34.7 Migration tests

Every migration must be tested on copies of worlds from previous released versions.

---

# 35. Northern Europe vertical slice

## 35.1 Proposed geographic extent

Initial detailed atlas target:

* Latitude: approximately 54° N to 72° N
* Longitude: approximately 25° W to 45° E

This includes:

* Sweden
* Norway
* Denmark
* Finland
* Iceland
* Baltic states
* Northern Germany
* Northern Poland
* Northwestern Russia
* Baltic Sea
* North Sea
* Norwegian Sea
* Parts of the Barents Sea

## 35.2 Why this region is suitable

It tests:

* Fjords
* Mountains
* Glaciers
* Boreal forest
* Temperate forest
* Tundra
* Archipelagos
* Thousands of lakes
* Wetlands
* Arctic daylight
* Strong seasons
* North Atlantic climate
* Continental climate
* Baltic bathymetry
* Offshore reservoirs
* Iron and copper provinces
* Volcanic Icelandic geology
* Karst and non-karst caves
* Reconstructed farmland

## 35.3 Vertical-slice completion criteria

The Scandinavian vertical slice is complete when it provides:

* Recognisable landforms
* Connected major hydrology
* Working ocean bathymetry
* Natural vegetation reconstruction
* Climate-derived biomes
* Regional caves
* Geological resources
* Soil properties
* Seasons and daylight
* Local weather
* Agriculture
* Wildlife categories
* Placeholder resources
* Safe spawn selection
* Stable singleplayer
* Stable ten-player dedicated-server test

---

# 36. Development roadmap

## Phase 0 — Foundation

### Deliverables

* NeoForge 21.1.244 workspace
* Java 21 toolchain
* Working client run
* Working dedicated-server run
* Project modules
* Git repository
* Continuous integration
* Formatting and static analysis
* Architecture decision records
* Dataset licensing matrix
* Basic documentation

### Exit criteria

* Empty Orbis Terrae mod loads in client and server.
* Build succeeds from a clean checkout.
* Automated tests run in CI.
* Project identifiers are correct.
* Client-only code cannot load on a dedicated server.

---

## Phase 1 — Atlas proof of concept

### Deliverables

* Atlas manifest schema
* Tile format prototype
* Atlas compiler CLI
* Elevation import
* Land mask import
* Global coarse test atlas
* Scandinavian detailed elevation atlas
* Java atlas reader
* Tile cache
* Coordinate conversion library

### Exit criteria

* A standalone test program samples elevation by latitude and longitude.
* Adjacent tile boundaries are continuous.
* Atlas output is deterministic.
* Corrupt tiles are detected.
* Runtime reader requires no GIS libraries.

---

## Phase 2 — Basic Earth dimension

### Deliverables

* Custom `BiomeSource`
* Custom `ChunkGenerator`
* Equirectangular coordinate mapping
* Horizontal scale profiles
* Basic vertical curve
* Land and ocean terrain
* No artificial structures
* Configurable geographic spawn
* Minimal profile loader
* Manifest creation

### Exit criteria

* The game creates an Orbis Terrae world.
* Scandinavia has recognisable large-scale terrain.
* The same seed and manifest reproduce identical chunks.
* Singleplayer and dedicated server both work.
* No internet is required.

### Suggested version

`0.2.0 — Terrain Alpha`

---

## Phase 3 — Hydrology and oceans

### Deliverables

* River-network import
* Lakes
* Watersheds
* Hydrological terrain correction
* River-width generalisation
* Ocean bathymetry
* Continental shelves
* Scandinavian fjords
* Baltic and North Sea profiles

### Exit criteria

* Major rivers connect from source region to mouth.
* Rivers do not visibly terminate at chunk boundaries.
* Lakes have valid outlets where appropriate.
* Fjords and major straits remain open.
* Ocean depth stays inside the configured vertical range.

---

## Phase 4 — Climate, biomes and vegetation

### Deliverables

* Monthly climate layers
* Climate interpolation
* Biome classifier
* Ecotones
* Farmland reconstruction
* Vegetation categories
* Regional tree and plant placement
* Climate debugging overlay

### Exit criteria

* Boreal, temperate, alpine and tundra regions appear plausibly.
* Biome borders are not abrupt map-like lines.
* Farmland is replaced with plausible native vegetation.
* Mountain and coastal climate effects are visible.

---

## Phase 5 — Geology, caves and resources

### Deliverables

* Geological atlas
* Lithology system
* Regional cave profiles
* Aquifers
* Geological provinces
* Deposit generators
* Placeholder ore blocks
* Reservoir metadata
* Survey debugging tools

### Exit criteria

* Cave styles differ by geological setting.
* Deposits remain deterministic.
* Resource types use appropriate shapes.
* Missing integrations produce valid placeholders.
* No resource crosses invalid geological constraints without an intentional fallback.

---

## Phase 6 — Soil

### Deliverables

* Soil atlas
* Broad visible soil blocks
* Hidden soil properties
* Soil inspection tool
* Modified-soil persistence
* Moisture and fertility system

### Exit criteria

* Soil values follow atlas inputs.
* Modified soil persists after restart.
* Unmodified soil does not create excessive save data.
* Soil properties can be inspected in debug mode.

---

## Phase 7 — Seasons and local daylight

### Deliverables

* Astronomical calendar
* Local solar time
* Hemisphere seasons
* Latitude-dependent day length
* Polar day and night
* Client sky rendering
* Seasonal temperature modifier
* Bed behaviour

### Exit criteria

* Players at different longitudes see different solar times.
* Northern and Southern seasons are opposite.
* High-latitude daylight varies by season.
* Beds set spawn and do not skip the global calendar.
* Dedicated server remains authoritative.

---

## Phase 8 — Local weather

### Deliverables

* Weather-cell system
* Local rain and snow
* Wind
* Rain shadows
* Thunderstorms
* Blizzards
* Heatwaves
* Cold waves
* Drought
* Severe-event configuration
* Client weather rendering

### Exit criteria

* Players in distant regions can experience different weather.
* Weather simulation cost scales with loaded regions.
* Inactive global cells do not tick continuously.
* Weather state survives restart where necessary.

---

## Phase 9 — Agriculture

### Deliverables

* Crop registry
* Climate suitability
* Soil suitability
* Seasonal growth
* Irrigation
* Fertilisers
* Heating
* Artificial lighting
* Greenhouses
* Yield calculation
* Crop information screen

### Exit criteria

* Unsuitable conditions reduce or prevent crop growth.
* Greenhouses and irrigation provide meaningful mitigation.
* Vanilla crops have complete profiles.
* Modded crops can be added without Java code.

---

## Phase 10 — Wildlife and wildfires

### Deliverables

* Habitat categories
* Animal mappings
* Carrying-cap approximations
* Seasonal spawn modifiers
* Wildfire ignition
* Loaded-chunk fire simulation
* Regrowth
* Safety limits

### Exit criteria

* Animals appear in suitable regions.
* Population caps prevent uncontrolled accumulation.
* Fire respects weather and vegetation.
* Fire cannot consume unlimited unloaded terrain.
* Recovery works.

---

## Phase 11 — Wrapping and polar behaviour

### Deliverables

* Functional longitude crossing
* Destination preload
* Soft visual transition
* Vehicles and passengers
* Compressed polar caps
* Pole crossing
* Seam tests

### Exit criteria

* Players can cross east–west without losing inventory or velocity.
* Terrain matches on both sides of the seam.
* The transition is understandable and not disorienting.
* Polar travel does not create invalid coordinates.

Full cross-seam redstone, fluids and projectile simulation remain later work.

---

## Phase 12 — World wizard and pregeneration

### Deliverables

* Full setup GUI
* Profile import and export
* Validation report
* Storage estimator
* Time estimator
* Lazy, regional and full modes
* Resumable pregeneration
* Progress display
* TPS throttling

### Exit criteria

* Invalid profiles cannot create worlds.
* Estimates are based on local benchmarks.
* Pregeneration can recover after restart.
* Extreme configurations require an advanced override.
* Live-server performance limits are respected.

---

## Phase 13 — Official compatibility examples

### Deliverables

* Public compatibility API
* Compatibility documentation
* Mekanism module
* Immersive Engineering module
* Example community datapack
* Compatibility testing matrix

### Exit criteria

* Core mod runs without either optional mod.
* Each integration loads only with its target mod.
* Placeholder conversion is deliberate and backed up.
* Community authors can add crops and animals through JSON.

---

## Phase 14 — Global expansion and stabilization

### Deliverables

* Global atlas completion
* Regional override framework
* Performance optimization
* World migration framework
* User documentation
* Server administration guide
* Attribution notices
* Crash diagnostics
* Release candidate

### Exit criteria

* Global Compact and Global Survival pass exploration tests.
* Scandinavian detail remains superior to the global fallback.
* Ten-player server test remains playable.
* No critical save corruption bugs remain.
* Atlas provenance and licensing are documented.

### Suggested version

`1.0.0 — First Stable Release`

---

# 37. Release sequence

| Version | Main objective                            |
| ------- | ----------------------------------------- |
| `0.1.x` | Atlas and coordinate tools                |
| `0.2.x` | Basic Earth terrain                       |
| `0.3.x` | Hydrology and oceans                      |
| `0.4.x` | Climate, biomes and vegetation            |
| `0.5.x` | Geology, caves and resources              |
| `0.6.x` | Soil, seasons and local time              |
| `0.7.x` | Weather and agriculture                   |
| `0.8.x` | Wildlife, fire and compatibility          |
| `0.9.x` | Wrapping, pregeneration and stabilization |
| `1.0.0` | Stable documented release                 |

---

# 38. Risk register

| Risk                                   | Probability | Impact   | Mitigation                                                  |
| -------------------------------------- | ----------- | -------- | ----------------------------------------------------------- |
| Scope becomes unmanageable             | Very high   | Critical | Strict phase gates and postponed features                   |
| GIS learning curve                     | High        | High     | Separate compiler, small test datasets, documented pipeline |
| Dataset redistribution restrictions    | High        | Critical | Licensing matrix before use                                 |
| Poor chunk performance                 | High        | Critical | Binary atlas, caching, benchmarks from Phase 1              |
| Expanded height causes lag             | Medium      | High     | Benchmark profiles before locking dimensions                |
| Rivers fail at compressed scales       | High        | High     | Topological hydrology and scale-aware generalisation        |
| Local sky conflicts with vanilla logic | Medium      | High     | Custom client rendering and server-owned calendar           |
| Weather becomes too expensive          | High        | High     | Active-cell simulation only                                 |
| Longitude wrap remains visibly awkward | High        | Medium   | Functional transition first, seamless rendering later       |
| Mod integrations break                 | High        | Medium   | Separate optional artifacts and schema versioning           |
| Atlas updates damage worlds            | Medium      | Critical | Manifest version lock and explicit migration                |
| Placeholder changes corrupt saves      | Medium      | Critical | Registry stability and migration backups                    |
| 1:1 creates unrealistic expectations   | High        | Medium   | Clear warnings and lazy-generation recommendation           |
| Data is inconsistent between countries | High        | Medium   | Global fallback plus regional overrides                     |
| Solo development stops                 | Medium      | Critical | Open-source-ready repository and documentation              |

---

# 39. Scope-control rules

1. No new major feature enters a milestone without acceptance criteria.
2. No global environmental system is expanded before it works in Scandinavia.
3. No natural wonders before the Scandinavian vertical slice is complete.
4. No extreme-height branch before ordinary expanded height is stable.
5. No seamless cross-seam simulation before functional wrapping works.
6. No complete ecosystem simulation before habitat spawning is stable.
7. No automatic world migration.
8. No second Minecraft version before 1.21.1 reaches stability.
9. No second loader before the NeoForge release is mature.
10. Performance regressions block feature releases.

---

# 40. Documentation requirements

The repository should maintain:

* Installation guide
* Developer setup guide
* Server guide
* World-profile reference
* Atlas-format specification
* Dataset provenance
* Licensing and attribution
* Compatibility-pack guide
* Crop schema guide
* Wildlife schema guide
* Resource schema guide
* Migration guide
* Architecture decision records
* Performance benchmark history
* Known limitations

---

# 41. Open-source preparation

The project may remain private initially, but should be structured for publication.

Before public release:

* Choose a source-code licence.
* Add a contribution guide.
* Add a code of conduct.
* Create issue templates.
* Create pull-request templates.
* Separate source-code licensing from atlas-data licensing.
* Remove any dataset that cannot legally be redistributed.
* Document how the official atlas was produced.
* Publish compatibility schemas.
* Provide reproducible atlas compiler instructions where permitted.

A permissive licence such as Apache-2.0 encourages broad reuse. LGPL-3.0 provides stronger requirements that changes to the mod library remain available. This decision can remain open during private development.

---

# 42. Definition of the first playable prototype

The first playable prototype is intentionally smaller than the full project.

It must:

* Create a custom Orbis Terrae dimension.
* Load a bundled test atlas offline.
* Support Global Compact and Global Survival profiles.
* Generate recognisable Scandinavian land and sea.
* Use real elevation and coastline inputs.
* Apply a basic nonlinear vertical curve.
* Disable artificial structures.
* Generate deterministic chunks lazily.
* Allow a configurable Scandinavian spawn.
* Work in singleplayer.
* Work on a dedicated server.
* Include atlas and coordinate debugging commands.
* Survive save, shutdown and reload.
* Produce no obvious chunk-border seams.

It does not yet need:

* Seasons
* Local sunlight
* Agriculture
* Wildlife
* Detailed geology
* Seamless longitude wrapping
* Full pregeneration GUI
* Mekanism or Immersive Engineering support

---

# 43. Immediate implementation backlog

## Repository and environment

1. Create the repository.
2. Initialize the NeoForge 21.1.244 development kit.
3. Set Java toolchain to 21.
4. Set mod ID and metadata.
5. Verify `runClient`.
6. Verify `runServer`.
7. Configure Git ignore rules.
8. Add formatting checks.
9. Add automated build workflow.
10. Create module structure.

## Architecture

11. Write the first architecture decision record.
12. Define dependency direction between modules.
13. Create the world-profile schema.
14. Create the manifest schema.
15. Create the atlas-manifest schema.
16. Create dataset provenance format.

## GIS preparation

17. Install and learn a minimal GDAL-based workflow.
18. Download a small Scandinavian elevation test area.
19. Convert it to a normalized raster.
20. Remove invalid and no-data values.
21. Export a small prototype tile set.
22. Verify latitude–longitude sampling.
23. Render test images outside Minecraft.
24. Record source and licence information.

## Atlas implementation

25. Implement atlas manifest reading.
26. Implement tile indexing.
27. Implement elevation-tile decoding.
28. Implement bounded tile cache.
29. Add tile-boundary tests.
30. Add deterministic checksums.

## Minecraft prototype

31. Register the Orbis Terrae chunk generator codec.
32. Register the biome-source codec.
33. Create a flat test Earth dimension.
34. Load the world manifest.
35. Convert chunk coordinates to geographic coordinates.
36. Sample elevation.
37. Fill terrain columns.
38. Add ocean level.
39. Disable structures.
40. Add a coordinate debug command.
41. Add configurable Scandinavian spawn.
42. Test save and reload.
43. Test dedicated-server generation.
44. Benchmark chunk generation.
45. Document results before adding hydrology.

---

# 44. First development gate

Development should not proceed to hydrology until all of the following are true:

* Clean project builds are reproducible.
* The atlas compiler and runtime reader are separate.
* Terrain is deterministic.
* No chunk-border elevation seams are visible.
* Atlas lookups are sufficiently fast.
* Singleplayer and dedicated-server saves reopen correctly.
* The manifest prevents incompatible settings from silently changing.
* The Scandinavian coastline is recognisable.
* The project has a documented dataset-provenance process.

---

# 45. Final approved specification

Orbis Terrae is a NeoForge 1.21.1 world-generation and environmental simulation mod that creates a deterministic, configurable, human-free Earth from an offline preprocessed atlas.

Its defining features are:

* Configurable geographic scale, including horizontally 1:1
* Independent nonlinear vertical scaling
* Real terrain, water, climate and geological regions
* Cartographic preservation of important features
* Natural vegetation reconstruction
* Regional caves and deposits
* Placeholder and mod-integrated resources
* Soil and agriculture simulation
* Regional seasons and local time
* Local weather and severe events
* Habitat-sensitive wildlife
* Configurable spawns
* Lazy generation by default
* Optional regional and total pregeneration
* Version-locked worlds
* Data-driven compatibility
* A global base atlas with a detailed Scandinavian development region

The project begins with terrain and atlas infrastructure. Every later environmental system builds on that stable geographic foundation.
