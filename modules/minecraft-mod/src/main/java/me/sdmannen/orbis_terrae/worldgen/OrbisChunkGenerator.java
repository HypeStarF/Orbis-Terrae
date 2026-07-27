package me.sdmannen.orbis_terrae.worldgen;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntimeManager;
import me.sdmannen.orbis_terrae.worldgen.spawn.GeographicSpawnResolver;
import me.sdmannen.orbis_terrae.worldgen.spawn.SpawnConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Serializable Earth generator that fills deterministic atlas-backed terrain columns. */
public final class OrbisChunkGenerator extends ChunkGenerator {
    public static final MapCodec<OrbisChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(OrbisChunkGenerator::getBiomeSource),
                    Codec.STRING.fieldOf("profile")
                            .forGetter(generator -> generator.profile.id()),
                    SpawnConfiguration.codec().optionalFieldOf("spawn", SpawnConfiguration.BUNDLED_BERGEN)
                            .forGetter(OrbisChunkGenerator::spawnConfiguration))
                    .apply(instance, OrbisChunkGenerator::new));

    private static final EnumSet<Heightmap.Types> GENERATED_HEIGHTMAPS = EnumSet.of(
            Heightmap.Types.OCEAN_FLOOR_WG,
            Heightmap.Types.WORLD_SURFACE_WG);

    private final WorldProfile profile;
    private final SpawnConfiguration spawnConfiguration;
    private transient volatile EarthAtlasSampler atlasSampler;

    public OrbisChunkGenerator(BiomeSource biomeSource, String profileId) {
        this(biomeSource, profileId, SpawnConfiguration.BUNDLED_BERGEN);
    }

    public OrbisChunkGenerator(
            BiomeSource biomeSource,
            String profileId,
            SpawnConfiguration spawnConfiguration) {
        super(biomeSource);
        profile = WorldProfiles.require(profileId);
        this.spawnConfiguration = Objects.requireNonNull(spawnConfiguration, "spawnConfiguration");
    }

    public WorldProfile profile() {
        return profile;
    }

    public SpawnConfiguration spawnConfiguration() {
        return spawnConfiguration;
    }

    /** Samples all available atlas inputs for one Minecraft block column. */
    public EarthAtlasSampler.ColumnSample sampleAtlasColumn(long blockX, long blockZ) throws IOException {
        return atlasSampler().sample(blockX, blockZ);
    }

    /** Resolves the complete deterministic terrain plan for one Minecraft block column. */
    public TerrainColumnPlan planTerrainColumn(long blockX, long blockZ) {
        try {
            return TerrainColumnPlan.from(profile, sampleAtlasColumn(blockX, blockZ));
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to sample Orbis Terrae atlas at " + blockX + ", " + blockZ,
                    exception);
        }
    }

    /** Resolves the configured geographic spawn, falling back to the bundled Bergen target when necessary. */
    public GeographicSpawnResolver.SpawnResolution resolveSpawn() {
        Optional<GeographicSpawnResolver.SpawnResolution> configured = GeographicSpawnResolver.resolve(
                profile,
                spawnConfiguration,
                this::planTerrainColumn);
        if (configured.isPresent()) {
            return configured.orElseThrow();
        }

        if (!spawnConfiguration.equals(SpawnConfiguration.BUNDLED_BERGEN)) {
            Optional<GeographicSpawnResolver.SpawnResolution> bundledFallback = GeographicSpawnResolver.resolve(
                    profile,
                    SpawnConfiguration.BUNDLED_BERGEN,
                    this::planTerrainColumn);
            if (bundledFallback.isPresent()) {
                return bundledFallback.orElseThrow();
            }
        }

        throw new IllegalStateException(
                "No safe Orbis Terrae spawn exists near the configured target or bundled Bergen fallback");
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(
            WorldGenRegion level,
            long seed,
            RandomState random,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step) {
        // Caves and terrain carving are deliberately deferred beyond the first solid-column prototype.
    }

    @Override
    public void buildSurface(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState random,
            ChunkAccess chunk) {
        // Grass, dirt, and seabed materials are placed directly during fillFromNoise.
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk) {
        try {
            fillChunk(chunk);
            return CompletableFuture.completedFuture(chunk);
        } catch (UncheckedIOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        // Natural population is deferred until biome and spawn rules are introduced.
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager) {
        // Artificial structure starts are disabled for the Orbis Terrae dimension.
    }

    @Override
    public void createReferences(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkAccess chunk) {
        // Structure references remain empty because Orbis Terrae creates no artificial structure starts.
    }

    @Override
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
            ServerLevel level,
            HolderSet<Structure> structures,
            BlockPos position,
            int searchRadius,
            boolean skipReferencedStructures) {
        return null;
    }

    @Override
    public int getGenDepth() {
        return profile.height();
    }

    @Override
    public int getMinY() {
        return profile.minimumY();
    }

    @Override
    public int getSeaLevel() {
        return profile.seaLevel();
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types type,
            LevelHeightAccessor level,
            RandomState random) {
        TerrainColumnPlan plan = planTerrainColumn(x, z);
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) {
            return plan.oceanFloorHeight();
        }
        return plan.worldSurfaceHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            LevelHeightAccessor height,
            RandomState random) {
        TerrainColumnPlan plan = planTerrainColumn(x, z);
        int minimumY = height.getMinBuildHeight();
        BlockState[] states = new BlockState[height.getHeight()];
        for (int index = 0; index < states.length; index++) {
            states[index] = blockState(plan.blockRoleAt(minimumY + index));
        }
        return new NoiseColumn(minimumY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Orbis Terrae profile: " + profile.id());
        try {
            EarthAtlasSampler.ColumnSample sample = sampleAtlasColumn(pos.getX(), pos.getZ());
            TerrainColumnPlan plan = TerrainColumnPlan.from(profile, sample);
            info.add(String.format(
                    Locale.ROOT,
                    "Orbis Terrae coordinate: %.5f, %.5f",
                    sample.geographic().latitude(),
                    sample.geographic().longitude()));
            info.add(sample.elevation()
                    .map(elevation -> String.format(
                            Locale.ROOT,
                            "Orbis Terrae elevation: %.2f m -> Y %d (%s)",
                            elevation.metres(),
                            elevation.terrainY(),
                            elevation.atlasId()))
                    .orElse("Orbis Terrae elevation: unavailable"));
            info.add(sample.landMask()
                    .map(landMask -> "Orbis Terrae land mask: "
                            + (landMask.land() ? "land" : "water")
                            + " (" + landMask.atlasId() + ")")
                    .orElse("Orbis Terrae land mask: unavailable"));
            info.add("Orbis Terrae column: "
                    + (plan.land() ? "land" : "ocean")
                    + ", solid top Y " + plan.solidTopY()
                    + ", data " + plan.dataAvailability().name().toLowerCase(Locale.ROOT));
        } catch (IOException exception) {
            info.add("Orbis Terrae atlas error: " + exception.getMessage());
        }
    }

    private void fillChunk(ChunkAccess chunk) {
        ChunkPos chunkPosition = chunk.getPos();
        int minimumY = Math.max(profile.minimumY(), chunk.getMinBuildHeight());
        int maximumY = Math.min(profile.maximumY(), chunk.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos mutablePosition = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int blockX = chunkPosition.getMinBlockX() + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int blockZ = chunkPosition.getMinBlockZ() + localZ;
                TerrainColumnPlan plan = planTerrainColumn(blockX, blockZ);
                int highestY = Math.min(maximumY, plan.highestNonAirY());
                for (int y = minimumY; y <= highestY; y++) {
                    BlockState state = blockState(plan.blockRoleAt(y));
                    if (!state.isAir()) {
                        chunk.setBlockState(mutablePosition.set(blockX, y, blockZ), state, false);
                    }
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, GENERATED_HEIGHTMAPS);
    }

    private EarthAtlasSampler atlasSampler() {
        EarthAtlasSampler current = atlasSampler;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = atlasSampler;
            if (current == null) {
                current = OrbisAtlasRuntimeManager.sampler(profile);
                atlasSampler = current;
            }
            return current;
        }
    }

    private static BlockState blockState(TerrainColumnPlan.BlockRole role) {
        return switch (role) {
            case STONE -> Blocks.STONE.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
        };
    }
}
