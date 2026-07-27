package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;
import me.sdmannen.orbis_terrae.atlas.selection.AtlasStack;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.geo.EarthCoordinateMapper;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.atlas.BundledAtlasInstaller;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Phase2AtlasSamplingTest {
    private static final Path MULTI_TILE_FIXTURE = Path.of(
            "..", "..", "atlas", "test-fixtures", "multi-tile-v1");

    @Test
    void samplesCommittedMultiTileFixtureAtMinecraftOrigin() throws IOException {
        EarthAtlasSampler sampler = fixtureSampler();

        EarthAtlasSampler.ColumnSample sample = sampler.sample(0, 0);

        assertEquals(0, sample.requestedBlockX());
        assertEquals(0, sample.wrappedBlockX());
        assertEquals(0, sample.blockZ());
        assertTrue(sample.insideProjectedLatitude());
        assertEquals(0.0, sample.geographic().latitude());
        assertEquals(0.0, sample.geographic().longitude());
        assertTrue(sample.hasCompleteTerrainInput());

        EarthAtlasSampler.ElevationSample elevation = sample.elevation().orElseThrow();
        assertEquals(300.0, elevation.metres());
        assertEquals(96, elevation.terrainY());
        assertEquals("phase1-multi-tile-fixture", elevation.atlasId());

        EarthAtlasSampler.LandMaskSample landMask = sample.landMask().orElseThrow();
        assertFalse(landMask.land());
        assertEquals("phase1-multi-tile-fixture", landMask.atlasId());
    }

    @Test
    void wrapsLongitudeAndKeepsNoDataIndependentFromLandMask() throws IOException {
        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        EarthAtlasSampler sampler = fixtureSampler();
        int worldWidth = profile.horizontalScale().projectedWidthBlocks();

        EarthAtlasSampler.ColumnSample origin = sampler.sample(0, 0);
        EarthAtlasSampler.ColumnSample wrapped = sampler.sample(worldWidth, 0);

        assertEquals(0, wrapped.wrappedBlockX());
        assertEquals(origin.geographic(), wrapped.geographic());
        assertEquals(origin.elevation(), wrapped.elevation());
        assertEquals(origin.landMask(), wrapped.landMask());

        EarthCoordinateMapper mapper = new EarthCoordinateMapper(profile);
        EarthCoordinateMapper.BlockCoordinate noDataBlock =
                mapper.toBlock(new GeoCoordinate(0.0, 3.0));
        EarthAtlasSampler.ColumnSample noData = sampler.sample(
                Math.round(noDataBlock.x()),
                Math.round(noDataBlock.z()));

        assertTrue(noData.elevation().isEmpty());
        assertTrue(noData.landMask().orElseThrow().land());
        assertFalse(noData.hasCompleteTerrainInput());

        EarthAtlasSampler.ColumnSample outsideLatitude = sampler.sample(
                0,
                mapper.maximumZExclusive() + 1L);
        assertFalse(outsideLatitude.insideProjectedLatitude());
        assertTrue(outsideLatitude.elevation().isEmpty());
        assertTrue(outsideLatitude.landMask().isEmpty());
    }

    @Test
    void installsReusesAndSamplesBundledBergenAtlas(@TempDir Path temporary) throws IOException {
        Path gameDirectory = temporary.resolve("game");
        OrbisAtlasRuntime first = OrbisAtlasRuntime.openBundled(gameDirectory);
        OrbisAtlasRuntime second = OrbisAtlasRuntime.openBundled(gameDirectory);

        assertEquals(List.of(BundledAtlasInstaller.BUNDLED_ATLAS_ID), first.atlasIds());
        assertEquals(first.atlasDirectories(), second.atlasDirectories());
        assertTrue(first.atlasDirectories().getFirst().startsWith(gameDirectory));

        WorldProfile profile = WorldProfiles.GLOBAL_SURVIVAL;
        EarthCoordinateMapper mapper = new EarthCoordinateMapper(profile);
        EarthCoordinateMapper.BlockCoordinate bergenBlock =
                mapper.toBlock(new GeoCoordinate(60.3913, 5.3221));
        EarthAtlasSampler.ColumnSample bergen = first.sampler(profile).sample(
                Math.round(bergenBlock.x()),
                Math.round(bergenBlock.z()));

        assertTrue(bergen.hasCompleteTerrainInput());
        assertTrue(bergen.landMask().orElseThrow().land());
        assertEquals(
                BundledAtlasInstaller.BUNDLED_ATLAS_ID,
                bergen.elevation().orElseThrow().atlasId());
        assertEquals(
                BundledAtlasInstaller.BUNDLED_ATLAS_ID,
                bergen.landMask().orElseThrow().atlasId());
        assertTrue(bergen.elevation().orElseThrow().terrainY() >= profile.seaLevel());
        assertEquals(60.3913, bergen.geographic().latitude(), 0.01);
        assertEquals(5.3221, bergen.geographic().longitude(), 0.01);
    }

    private static EarthAtlasSampler fixtureSampler() throws IOException {
        AtlasDirectory atlas = AtlasDirectory.open(MULTI_TILE_FIXTURE);
        return new EarthAtlasSampler(
                WorldProfiles.GLOBAL_SURVIVAL,
                new AtlasStack(List.of(atlas)));
    }
}
