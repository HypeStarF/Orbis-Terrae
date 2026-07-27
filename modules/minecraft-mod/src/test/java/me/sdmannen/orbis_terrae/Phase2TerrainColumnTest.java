package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.TerrainColumnPlan;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;
import org.junit.jupiter.api.Test;

final class Phase2TerrainColumnTest {
    private static final WorldProfile PROFILE = WorldProfiles.GLOBAL_SURVIVAL;

    @Test
    void clampsCompleteLandToSeaLevelAndBuildsSoilLayers() {
        TerrainColumnPlan plan = TerrainColumnPlan.from(
                PROFILE,
                sample(40, true, true));

        assertTrue(plan.land());
        assertEquals(PROFILE.seaLevel(), plan.solidTopY());
        assertEquals(TerrainColumnPlan.DataAvailability.COMPLETE, plan.dataAvailability());
        assertEquals(TerrainColumnPlan.BlockRole.GRASS, plan.blockRoleAt(PROFILE.seaLevel()));
        assertEquals(TerrainColumnPlan.BlockRole.DIRT, plan.blockRoleAt(PROFILE.seaLevel() - 1));
        assertEquals(TerrainColumnPlan.BlockRole.STONE, plan.blockRoleAt(PROFILE.seaLevel() - 4));
        assertEquals(TerrainColumnPlan.BlockRole.AIR, plan.blockRoleAt(PROFILE.seaLevel() + 1));
        assertEquals(PROFILE.seaLevel() + 1, plan.worldSurfaceHeight());
        assertEquals(PROFILE.seaLevel() + 1, plan.oceanFloorHeight());
    }

    @Test
    void clampsCompleteOceanBelowSeaLevelAndFillsWater() {
        TerrainColumnPlan plan = TerrainColumnPlan.from(
                PROFILE,
                sample(120, false, true));

        assertFalse(plan.land());
        assertEquals(PROFILE.seaLevel() - 1, plan.solidTopY());
        assertEquals(TerrainColumnPlan.BlockRole.SAND, plan.blockRoleAt(PROFILE.seaLevel() - 1));
        assertEquals(TerrainColumnPlan.BlockRole.WATER, plan.blockRoleAt(PROFILE.seaLevel()));
        assertEquals(TerrainColumnPlan.BlockRole.AIR, plan.blockRoleAt(PROFILE.seaLevel() + 1));
        assertEquals(PROFILE.seaLevel() + 1, plan.worldSurfaceHeight());
        assertEquals(PROFILE.seaLevel(), plan.oceanFloorHeight());
    }

    @Test
    void usesFlatSeaLevelLandWhenOnlyLandMaskIsAvailable() {
        EarthAtlasSampler.ColumnSample sample = new EarthAtlasSampler.ColumnSample(
                0,
                0,
                0,
                true,
                new GeoCoordinate(0.0, 0.0),
                Optional.empty(),
                Optional.of(new EarthAtlasSampler.LandMaskSample(true, "test")));

        TerrainColumnPlan plan = TerrainColumnPlan.from(PROFILE, sample);

        assertTrue(plan.land());
        assertEquals(PROFILE.seaLevel(), plan.solidTopY());
        assertEquals(TerrainColumnPlan.DataAvailability.LAND_MASK_ONLY, plan.dataAvailability());
    }

    @Test
    void usesDeterministicFallbackOceanWhenAtlasDataIsMissing() {
        EarthAtlasSampler.ColumnSample sample = new EarthAtlasSampler.ColumnSample(
                0,
                0,
                0,
                false,
                new GeoCoordinate(-90.0, 0.0),
                Optional.empty(),
                Optional.empty());

        TerrainColumnPlan plan = TerrainColumnPlan.from(PROFILE, sample);

        assertFalse(plan.land());
        assertEquals(
                PROFILE.seaLevel() - TerrainColumnPlan.FALLBACK_OCEAN_DEPTH,
                plan.solidTopY());
        assertEquals(TerrainColumnPlan.DataAvailability.NO_DATA, plan.dataAvailability());
        assertEquals(TerrainColumnPlan.BlockRole.SAND, plan.blockRoleAt(plan.solidTopY()));
        assertEquals(TerrainColumnPlan.BlockRole.WATER, plan.blockRoleAt(PROFILE.seaLevel()));
        assertEquals(PROFILE.seaLevel() + 1, plan.worldSurfaceHeight());
    }

    private static EarthAtlasSampler.ColumnSample sample(
            int terrainY,
            boolean land,
            boolean includeLandMask) {
        Optional<EarthAtlasSampler.LandMaskSample> landMask = includeLandMask
                ? Optional.of(new EarthAtlasSampler.LandMaskSample(land, "test"))
                : Optional.empty();
        return new EarthAtlasSampler.ColumnSample(
                0,
                0,
                0,
                true,
                new GeoCoordinate(0.0, 0.0),
                Optional.of(new EarthAtlasSampler.ElevationSample(0.0, terrainY, "test")),
                landMask);
    }
}
