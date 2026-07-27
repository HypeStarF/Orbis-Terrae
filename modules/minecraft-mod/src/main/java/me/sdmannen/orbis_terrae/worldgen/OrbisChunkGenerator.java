package me.sdmannen.orbis_terrae.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseColumn;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.StructureManager;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Serializable chunk-generator boundary; terrain generation is added in later Phase 2 steps. */
public final class OrbisChunkGenerator extends ChunkGenerator {
    public static final MapCodec<OrbisChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(OrbisChunkGenerator::getBiomeSource),
                    Codec.STRING.fieldOf("profile")
                            .forGetter(generator -> generator.profile.id()))
                    .apply(instance, OrbisChunkGenerator::new));

    private static final String TERRAIN_UNAVAILABLE =
            "Orbis Terrae terrain generation is not implemented until Phase 2 Steps 4 and 5";

    private final WorldProfile profile;

    public OrbisChunkGenerator(BiomeSource biomeSource, String profileId) {
        super(biomeSource);
        profile = WorldProfiles.require(profileId);
    }

    public WorldProfile profile() {
        return profile;
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
        throw terrainUnavailable();
    }

    @Override
    public void buildSurface(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState random,
            ChunkAccess chunk) {
        throw terrainUnavailable();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk) {
        throw terrainUnavailable();
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        throw terrainUnavailable();
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
        throw terrainUnavailable();
    }

    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            LevelHeightAccessor height,
            RandomState random) {
        throw terrainUnavailable();
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Orbis Terrae profile: " + profile.id());
        info.add("Orbis Terrae terrain pipeline: pending Phase 2 Steps 4 and 5");
    }

    private static UnsupportedOperationException terrainUnavailable() {
        return new UnsupportedOperationException(TERRAIN_UNAVAILABLE);
    }
}
