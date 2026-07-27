package me.sdmannen.orbis_terrae.worldgen;

import java.util.Objects;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;

/** Deterministic material and height plan for one generated Earth column. */
public record TerrainColumnPlan(
        boolean land,
        int solidTopY,
        int seaLevel,
        int minimumY,
        int maximumY,
        DataAvailability dataAvailability) {
    public static final int FALLBACK_OCEAN_DEPTH = 16;
    private static final int SURFACE_DEPTH = 4;

    public TerrainColumnPlan {
        Objects.requireNonNull(dataAvailability, "dataAvailability");
        if (minimumY > maximumY) {
            throw new IllegalArgumentException("minimumY must not exceed maximumY");
        }
        if (seaLevel < minimumY || seaLevel > maximumY) {
            throw new IllegalArgumentException("seaLevel must be inside the column range");
        }
        if (solidTopY < minimumY || solidTopY > maximumY) {
            throw new IllegalArgumentException("solidTopY must be inside the column range");
        }
        if (land && solidTopY < seaLevel) {
            throw new IllegalArgumentException("Land columns must reach sea level");
        }
        if (!land && solidTopY >= seaLevel) {
            throw new IllegalArgumentException("Ocean columns must leave room for water");
        }
    }

    public static TerrainColumnPlan from(
            WorldProfile profile,
            EarthAtlasSampler.ColumnSample sample) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sample, "sample");

        boolean hasElevation = sample.elevation().isPresent();
        boolean hasLandMask = sample.landMask().isPresent();
        boolean land = sample.landMask()
                .map(EarthAtlasSampler.LandMaskSample::land)
                .orElse(false);
        int rawSolidTop = sample.elevation()
                .map(EarthAtlasSampler.ElevationSample::terrainY)
                .orElse(profile.seaLevel() - FALLBACK_OCEAN_DEPTH);
        int solidTop = land
                ? Math.max(profile.seaLevel(), rawSolidTop)
                : Math.min(profile.seaLevel() - 1, rawSolidTop);
        solidTop = Math.max(profile.minimumY(), Math.min(profile.maximumY(), solidTop));

        return new TerrainColumnPlan(
                land,
                solidTop,
                profile.seaLevel(),
                profile.minimumY(),
                profile.maximumY(),
                DataAvailability.from(hasElevation, hasLandMask));
    }

    public BlockRole blockRoleAt(int y) {
        if (y < minimumY || y > maximumY) {
            return BlockRole.AIR;
        }
        if (y > solidTopY) {
            return !land && y <= seaLevel ? BlockRole.WATER : BlockRole.AIR;
        }

        int depth = solidTopY - y;
        if (land) {
            if (depth == 0) {
                return BlockRole.GRASS;
            }
            if (depth < SURFACE_DEPTH) {
                return BlockRole.DIRT;
            }
            return BlockRole.STONE;
        }
        return depth < SURFACE_DEPTH ? BlockRole.SAND : BlockRole.STONE;
    }

    public int oceanFloorHeight() {
        return solidTopY + 1;
    }

    public int worldSurfaceHeight() {
        return highestNonAirY() + 1;
    }

    public int highestNonAirY() {
        return land ? solidTopY : seaLevel;
    }

    /** Availability of the two atlas inputs used to construct the plan. */
    public enum DataAvailability {
        COMPLETE,
        ELEVATION_ONLY,
        LAND_MASK_ONLY,
        NO_DATA;

        private static DataAvailability from(boolean elevation, boolean landMask) {
            if (elevation && landMask) {
                return COMPLETE;
            }
            if (elevation) {
                return ELEVATION_ONLY;
            }
            if (landMask) {
                return LAND_MASK_ONLY;
            }
            return NO_DATA;
        }
    }

    /** Minecraft-independent block role selected for a vertical coordinate. */
    public enum BlockRole {
        STONE,
        DIRT,
        GRASS,
        SAND,
        WATER,
        AIR
    }
}
