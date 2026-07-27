package me.sdmannen.orbis_terrae.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Serializable Earth generator with atlas sampling ready for deterministic terrain filling. */
public final class OrbisChunkGenerator extends ChunkGenerator {
    public static final MapCodec<OrbisChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(OrbisChunkGenerator::getBiomeSource),
                    Codec.STRING.fieldOf("profile")
                            .forGetter(generator -> generator.profile.id()))
                    .apply(instance, OrbisChunkGenerator::new));

    private static final String TERRAIN_UNAVAILABLE =
            "Orbis Terrae terrain filling is not implemented until Phase 2 Step 5";

    private final WorldProfile profile;
    private transient volatile EarthAtlasSampler atlasSampler;

    public OrbisChunkGenerator(BiomeSource biomeSource, String profileId) {
        super(biomeSource);
        profile = WorldProfiles.require(profileId);
    }

    public WorldProfile profile() {
        return profile;
    }

    /** Samples all available atlas inputs for one Minecraft block column. */
    public EarthAtlasSampler.ColumnSample sampleAtlasColumn(long blockX, long blockZ) throws IOException {
        return atlasSampler().sample(blockX, blockZ);
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
        try {
            EarthAtlasSampler.ColumnSample sample = sampleAtlasColumn(pos.getX(), pos.getZ());
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
        } catch (IOException exception) {
            info.add("Orbis Terrae atlas error: " + exception.getMessage());
        }
        info.add("Orbis Terrae terrain filling: pending Phase 2 Step 5");
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

    private static UnsupportedOperationException terrainUnavailable() {
        return new UnsupportedOperationException(TERRAIN_UNAVAILABLE);
    }
}
