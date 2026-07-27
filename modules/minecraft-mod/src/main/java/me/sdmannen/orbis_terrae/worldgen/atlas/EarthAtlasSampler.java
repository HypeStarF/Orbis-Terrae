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
    public static final int RECONSTRUCTION_RADIUS_METRES = 3_000;
    public static final int MAXIMUM_RESIDUAL_BLOCKS = 4;

    private final WorldProfile profile;
    private final EarthCoordinateMapper coordinateMapper;
    private final AtlasStack atlasStack;
    private final int reconstructionRadiusBlocks;

    public EarthAtlasSampler(WorldProfile profile, AtlasStack atlasStack) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.coordinateMapper = new EarthCoordinateMapper(profile);
        this.atlasStack = Objects.requireNonNull(atlasStack, "atlasStack");
        this.reconstructionRadiusBlocks = Math.max(
                1,
                (int) Math.ceil((double) RECONSTRUCTION_RADIUS_METRES
                        / profile.horizontalScale().metresPerBlock()));
    }

    public WorldProfile profile() {
        return profile;
    }

    public List<String> atlasIds() {
        return atlasStack.atlasIds();
    }

    public int reconstructionRadiusBlocks() {
        return reconstructionRadiusBlocks;
    }

    /** Samples one Minecraft block column and applies scale-aware land-elevation reconstruction. */
    public ColumnSample sample(long blockX, long blockZ) throws IOException {
        ColumnSample center = sampleRaw(blockX, blockZ);
        if (!center.hasCompleteTerrainInput()
                || !center.landMask().orElseThrow().land()) {
            return center;
        }

        long weightedHeight = 0L;
        long totalWeight = 0L;
        for (int offsetX = -reconstructionRadiusBlocks;
                offsetX <= reconstructionRadiusBlocks;
                offsetX++) {
            for (int offsetZ = -reconstructionRadiusBlocks;
                    offsetZ <= reconstructionRadiusBlocks;
                    offsetZ++) {
                ColumnSample nearby = sampleRaw(
                        Math.addExact(blockX, offsetX),
                        Math.addExact(blockZ, offsetZ));
                if (nearby.elevation().isEmpty()) {
                    continue;
                }

                int weight = kernelWeight(offsetX) * kernelWeight(offsetZ);
                weightedHeight = Math.addExact(
                        weightedHeight,
                        Math.multiplyExact((long) nearby.elevation().orElseThrow().terrainY(), weight));
                totalWeight = Math.addExact(totalWeight, weight);
            }
        }

        if (totalWeight == 0L) {
            return center;
        }

        ElevationSample rawElevation = center.elevation().orElseThrow();
        int regionalHeight = Math.toIntExact(Math.round((double) weightedHeight / totalWeight));
        int residual = rawElevation.terrainY() - regionalHeight;
        int preservedResidual = Math.max(
                -MAXIMUM_RESIDUAL_BLOCKS,
                Math.min(MAXIMUM_RESIDUAL_BLOCKS, residual));
        int generalizedHeight = regionalHeight + preservedResidual;
        generalizedHeight = Math.max(profile.seaLevel(), Math.min(profile.maximumY(), generalizedHeight));

        return new ColumnSample(
                center.requestedBlockX(),
                center.blockZ(),
                center.wrappedBlockX(),
                center.insideProjectedLatitude(),
                center.geographic(),
                Optional.of(new ElevationSample(
                        rawElevation.metres(),
                        generalizedHeight,
                        rawElevation.atlasId())),
                center.landMask());
    }

    private ColumnSample sampleRaw(long blockX, long blockZ) throws IOException {
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

    private int kernelWeight(int offset) {
        return reconstructionRadiusBlocks + 1 - Math.abs(offset);
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

    /** Bilinear real elevation, reconstructed Minecraft terrain height, and supplying atlas. */
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
