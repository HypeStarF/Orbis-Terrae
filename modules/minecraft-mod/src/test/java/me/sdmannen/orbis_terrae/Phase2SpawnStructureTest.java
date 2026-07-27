package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.TerrainColumnPlan;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntime;
import me.sdmannen.orbis_terrae.worldgen.spawn.GeographicSpawnResolver;
import me.sdmannen.orbis_terrae.worldgen.spawn.SpawnConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Phase2SpawnStructureTest {
    private static final Path MAIN_SOURCE = Path.of(
            "src", "main", "java", "me", "sdmannen", "orbis_terrae");

    @Test
    void acceptsSafeConfiguredCoordinateWithoutSearching() {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        SpawnConfiguration configuration = new SpawnConfiguration(0.0, 0.0, 8);

        GeographicSpawnResolver.SpawnResolution resolution = GeographicSpawnResolver.resolve(
                        profile,
                        configuration,
                        (x, z) -> land(profile, 70))
                .orElseThrow();

        assertEquals(0, resolution.blockX());
        assertEquals(71, resolution.blockY());
        assertEquals(0, resolution.blockZ());
        assertEquals(0, resolution.searchDistanceBlocks());
    }

    @Test
    void searchesSquareRingsInDeterministicOrder() {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        SpawnConfiguration configuration = new SpawnConfiguration(0.0, 0.0, 4);

        GeographicSpawnResolver.SpawnResolution resolution = GeographicSpawnResolver.resolve(
                        profile,
                        configuration,
                        (x, z) -> x >= 1 && x <= 3 && z >= -1 && z <= 1
                                ? land(profile, 72)
                                : ocean(profile))
                .orElseThrow();

        assertEquals(2, resolution.blockX());
        assertEquals(73, resolution.blockY());
        assertEquals(0, resolution.blockZ());
        assertEquals(2, resolution.searchDistanceBlocks());
    }

    @Test
    void rejectsWaterEdgesAndExcessiveLocalSlope() {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        SpawnConfiguration configuration = new SpawnConfiguration(0.0, 0.0, 3);

        Optional<GeographicSpawnResolver.SpawnResolution> resolution = GeographicSpawnResolver.resolve(
                profile,
                configuration,
                (x, z) -> land(profile, Math.floorMod(x + z, 2) == 0 ? 70 : 90));

        assertTrue(resolution.isEmpty());
    }

    @Test
    void bundledBergenTargetResolvesAgainstRealAtlas(@TempDir Path temporary) throws IOException {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        OrbisAtlasRuntime runtime = OrbisAtlasRuntime.openBundled(temporary.resolve("game"));
        EarthAtlasSampler sampler = runtime.sampler(profile);

        GeographicSpawnResolver.SpawnResolution resolution = GeographicSpawnResolver.resolve(
                        profile,
                        SpawnConfiguration.BUNDLED_BERGEN,
                        (x, z) -> {
                            try {
                                return TerrainColumnPlan.from(profile, sampler.sample(x, z));
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        })
                .orElseThrow();

        assertEquals(SpawnConfiguration.BUNDLED_BERGEN, resolution.configuration());
        assertTrue(resolution.searchDistanceBlocks()
                <= SpawnConfiguration.BUNDLED_BERGEN.searchRadiusBlocks());
        assertTrue(TerrainColumnPlan.from(
                        profile,
                        sampler.sample(resolution.blockX(), resolution.blockZ()))
                .land());
    }

    @Test
    void structureAndSpawnHooksRemainExplicit() throws IOException {
        String generator = Files.readString(MAIN_SOURCE.resolve(
                Path.of("worldgen", "OrbisChunkGenerator.java")));
        String events = Files.readString(MAIN_SOURCE.resolve(
                Path.of("worldgen", "spawn", "OrbisSpawnEvents.java")));

        assertTrue(generator.contains("public void createStructures("));
        assertTrue(generator.contains("public void createReferences("));
        assertTrue(generator.contains("findNearestMapStructure("));
        assertTrue(generator.contains("SpawnConfiguration.CODEC.optionalFieldOf(\"spawn\""));
        assertTrue(events.contains("LevelEvent.CreateSpawnPosition"));
        assertTrue(events.contains("instanceof OrbisChunkGenerator"));
        assertTrue(events.contains("setDefaultSpawnPos"));
        assertTrue(events.contains("event.setCanceled(true)"));
        assertFalse(events.contains("net.minecraft.client"));
    }

    private static TerrainColumnPlan land(WorldProfile profile, int solidTopY) {
        return new TerrainColumnPlan(
                true,
                solidTopY,
                profile.seaLevel(),
                profile.minimumY(),
                profile.maximumY(),
                TerrainColumnPlan.DataAvailability.COMPLETE);
    }

    private static TerrainColumnPlan ocean(WorldProfile profile) {
        return new TerrainColumnPlan(
                false,
                profile.seaLevel() - 8,
                profile.seaLevel(),
                profile.minimumY(),
                profile.maximumY(),
                TerrainColumnPlan.DataAvailability.COMPLETE);
    }
}
