package me.sdmannen.orbis_terrae.worldgen.spawn;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import me.sdmannen.orbis_terrae.geo.EarthCoordinateMapper;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.worldgen.TerrainColumnPlan;

/** Resolves a deterministic safe land spawn near one configured geographic coordinate. */
public final class GeographicSpawnResolver {
    public static final int REQUIRED_CLEARANCE_BLOCKS = 2;
    public static final int MAX_NEIGHBOR_HEIGHT_DIFFERENCE = 8;

    private static final int[][] NEIGHBOR_OFFSETS = {
        {-1, -1}, {0, -1}, {1, -1},
        {-1, 0}, {1, 0},
        {-1, 1}, {0, 1}, {1, 1}
    };

    private GeographicSpawnResolver() {
    }

    public static Optional<SpawnResolution> resolve(
            WorldProfile profile,
            SpawnConfiguration configuration,
            TerrainProvider terrainProvider) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(terrainProvider, "terrainProvider");

        EarthCoordinateMapper mapper = new EarthCoordinateMapper(profile);
        EarthCoordinateMapper.BlockCoordinate requested = mapper.toBlock(configuration.coordinate());
        int centerX = Math.toIntExact(Math.round(requested.x()));
        int centerZ = Math.toIntExact(Math.round(requested.z()));
        Map<Long, TerrainColumnPlan> cache = new HashMap<>();

        Optional<SpawnResolution> center = evaluate(
                configuration,
                centerX,
                centerZ,
                centerX,
                centerZ,
                terrainProvider,
                cache);
        if (center.isPresent()) {
            return center;
        }

        for (int radius = 1; radius <= configuration.searchRadiusBlocks(); radius++) {
            int minimumX = centerX - radius;
            int maximumX = centerX + radius;
            int minimumZ = centerZ - radius;
            int maximumZ = centerZ + radius;

            for (int x = minimumX; x <= maximumX; x++) {
                Optional<SpawnResolution> result = evaluate(
                        configuration,
                        centerX,
                        centerZ,
                        x,
                        minimumZ,
                        terrainProvider,
                        cache);
                if (result.isPresent()) {
                    return result;
                }
            }
            for (int z = minimumZ + 1; z <= maximumZ; z++) {
                Optional<SpawnResolution> result = evaluate(
                        configuration,
                        centerX,
                        centerZ,
                        maximumX,
                        z,
                        terrainProvider,
                        cache);
                if (result.isPresent()) {
                    return result;
                }
            }
            for (int x = maximumX - 1; x >= minimumX; x--) {
                Optional<SpawnResolution> result = evaluate(
                        configuration,
                        centerX,
                        centerZ,
                        x,
                        maximumZ,
                        terrainProvider,
                        cache);
                if (result.isPresent()) {
                    return result;
                }
            }
            for (int z = maximumZ - 1; z > minimumZ; z--) {
                Optional<SpawnResolution> result = evaluate(
                        configuration,
                        centerX,
                        centerZ,
                        minimumX,
                        z,
                        terrainProvider,
                        cache);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<SpawnResolution> evaluate(
            SpawnConfiguration configuration,
            int centerX,
            int centerZ,
            int x,
            int z,
            TerrainProvider terrainProvider,
            Map<Long, TerrainColumnPlan> cache) {
        TerrainColumnPlan center = plan(x, z, terrainProvider, cache);
        if (!center.land() || center.solidTopY() > center.maximumY() - REQUIRED_CLEARANCE_BLOCKS) {
            return Optional.empty();
        }

        for (int[] offset : NEIGHBOR_OFFSETS) {
            TerrainColumnPlan neighbor = plan(
                    x + offset[0],
                    z + offset[1],
                    terrainProvider,
                    cache);
            if (!neighbor.land()
                    || Math.abs(neighbor.solidTopY() - center.solidTopY())
                    > MAX_NEIGHBOR_HEIGHT_DIFFERENCE) {
                return Optional.empty();
            }
        }

        return Optional.of(new SpawnResolution(
                configuration,
                x,
                center.solidTopY() + 1,
                z,
                Math.max(Math.abs(x - centerX), Math.abs(z - centerZ))));
    }

    private static TerrainColumnPlan plan(
            int x,
            int z,
            TerrainProvider terrainProvider,
            Map<Long, TerrainColumnPlan> cache) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        return cache.computeIfAbsent(key, ignored -> Objects.requireNonNull(
                terrainProvider.plan(x, z),
                "terrain plan"));
    }

    /** Supplies the generated terrain contract without requiring loaded Minecraft chunks. */
    @FunctionalInterface
    public interface TerrainProvider {
        TerrainColumnPlan plan(int blockX, int blockZ);
    }

    /** Safe block position selected from one requested geographic target. */
    public record SpawnResolution(
            SpawnConfiguration configuration,
            int blockX,
            int blockY,
            int blockZ,
            int searchDistanceBlocks) {
        public SpawnResolution {
            Objects.requireNonNull(configuration, "configuration");
            if (searchDistanceBlocks < 0) {
                throw new IllegalArgumentException("searchDistanceBlocks must not be negative");
            }
        }
    }
}
