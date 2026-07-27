package me.sdmannen.orbis_terrae.worldgen.atlas;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;
import me.sdmannen.orbis_terrae.atlas.selection.AtlasStack;
import me.sdmannen.orbis_terrae.geo.EarthCoordinateMapper;
import me.sdmannen.orbis_terrae.profile.WorldProfile;

/** Maps Minecraft columns to deterministic elevation and land-mask samples from an atlas stack. */
public final class EarthAtlasSampler {
    private final WorldProfile profile;
    private final EarthCoordinateMapper coordinateMapper;
    private final AtlasStack atlasStack;

    public EarthAtlasSampler(WorldProfile profile, AtlasStack atlasStack) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.coordinateMapper = new EarthCoordinateMapper(profile);
        this.atlasStack = Objects.requireNonNull(atlasStack, "atlasStack");
    }

    public WorldProfile profile() {
        return profile;
    }

    public List<String> atlasIds() {
        return atlasStack.atlasIds();
    }

    /** Samples one Minecraft block column without placing any blocks. */
    public ColumnSample sample(long blockX, long blockZ) throws IOException {
        long wrappedBlockX = coordinateMapper.wrapBlockX(blockX);
        GeoCoordinate geographic = coordinateMapper.toGeographic(blockX, blockZ);

        Optional<ElevationSample> elevation = atlasStack
                .sampleBilinearElevationMetres(geographic.latitude(), geographic.longitude())
                .map(sample -> new ElevationSample(
                        sample.metres(),
                        profile.terrainY(sample.metres()),
                        sample.atlasId()));
        Optional<LandMaskSample> landMask = atlasStack
                .sampleLandMask(geographic.latitude(), geographic.longitude())
                .map(sample -> new LandMaskSample(sample.land(), sample.atlasId()));

        return new ColumnSample(
                blockX,
                blockZ,
                wrappedBlockX,
                coordinateMapper.containsProjectedLatitude(blockZ),
                geographic,
                elevation,
                landMask);
    }

    /** Complete result for one horizontal Minecraft block coordinate. */
    public record ColumnSample(
            long requestedBlockX,
            long blockZ,
            long wrappedBlockX,
            boolean insideProjectedLatitude,
            GeoCoordinate geographic,
            Optional<ElevationSample> elevation,
            Optional<LandMaskSample> landMask) {
        public ColumnSample {
            Objects.requireNonNull(geographic, "geographic");
            Objects.requireNonNull(elevation, "elevation");
            Objects.requireNonNull(landMask, "landMask");
        }

        public boolean hasCompleteTerrainInput() {
            return elevation.isPresent() && landMask.isPresent();
        }
    }

    /** Bilinear real elevation, transformed Minecraft terrain height, and supplying atlas. */
    public record ElevationSample(double metres, int terrainY, String atlasId) {
        public ElevationSample {
            if (!Double.isFinite(metres)) {
                throw new IllegalArgumentException("Elevation must be finite");
            }
            Objects.requireNonNull(atlasId, "atlasId");
            if (atlasId.isBlank()) {
                throw new IllegalArgumentException("atlasId must not be blank");
            }
        }
    }

    /** Nearest land classification and supplying atlas. */
    public record LandMaskSample(boolean land, String atlasId) {
        public LandMaskSample {
            Objects.requireNonNull(atlasId, "atlasId");
            if (atlasId.isBlank()) {
                throw new IllegalArgumentException("atlasId must not be blank");
            }
        }
    }
}
