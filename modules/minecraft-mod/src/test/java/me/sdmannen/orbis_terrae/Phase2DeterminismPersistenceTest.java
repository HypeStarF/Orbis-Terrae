package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;
import me.sdmannen.orbis_terrae.geo.EarthCoordinateMapper;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.TerrainColumnPlan;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntime;
import me.sdmannen.orbis_terrae.worldgen.validation.TerrainChunkFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Phase2DeterminismPersistenceTest {
    @Test
    void bundledAtlasProducesIdenticalChunkFingerprintsAcrossFreshRuntimeDirectories(
            @TempDir Path temporary) throws IOException {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        EarthCoordinateMapper mapper = new EarthCoordinateMapper(profile);
        EarthCoordinateMapper.BlockCoordinate bergen = mapper.toBlock(new GeoCoordinate(60.3913, 5.3221));
        int chunkX = Math.floorDiv((int) Math.round(bergen.x()), 16);
        int chunkZ = Math.floorDiv((int) Math.round(bergen.z()), 16);

        OrbisAtlasRuntime firstRuntime = OrbisAtlasRuntime.openBundled(temporary.resolve("first-game"));
        OrbisAtlasRuntime secondRuntime = OrbisAtlasRuntime.openBundled(temporary.resolve("second-game"));
        EarthAtlasSampler firstSampler = firstRuntime.sampler(profile);
        EarthAtlasSampler secondSampler = secondRuntime.sampler(profile);

        String first = TerrainChunkFingerprint.compute(
                chunkX,
                chunkZ,
                (x, z) -> plan(profile, firstSampler, x, z));
        String repeated = TerrainChunkFingerprint.compute(
                chunkX,
                chunkZ,
                (x, z) -> plan(profile, firstSampler, x, z));
        String reopened = TerrainChunkFingerprint.compute(
                chunkX,
                chunkZ,
                (x, z) -> plan(profile, secondSampler, x, z));

        assertEquals(first, repeated);
        assertEquals(first, reopened);
        assertEquals(64, first.length());
    }

    @Test
    void fingerprintChangesWhenAnyColumnPlanChanges() {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        String flat = TerrainChunkFingerprint.compute(
                0,
                0,
                (x, z) -> land(profile, 70));
        String varied = TerrainChunkFingerprint.compute(
                0,
                0,
                (x, z) -> land(profile, x == 7 && z == 9 ? 71 : 70));
        String neighboringChunk = TerrainChunkFingerprint.compute(
                1,
                0,
                (x, z) -> land(profile, 70));

        assertNotEquals(flat, varied);
        assertNotEquals(flat, neighboringChunk);
    }

    private static TerrainColumnPlan plan(
            WorldProfile profile,
            EarthAtlasSampler sampler,
            int blockX,
            int blockZ) {
        try {
            return TerrainColumnPlan.from(profile, sampler.sample(blockX, blockZ));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
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
}
