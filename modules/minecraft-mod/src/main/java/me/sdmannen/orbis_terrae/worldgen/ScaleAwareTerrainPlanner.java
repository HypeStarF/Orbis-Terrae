package me.sdmannen.orbis_terrae.worldgen;

import java.io.IOException;
import java.util.Objects;
import me.sdmannen.orbis_terrae.profile.WorldProfile;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;

/**
 * Reconstructs broad terrain shapes appropriate for the active horizontal scale while preserving the
 * centre column's atlas-backed land or ocean classification.
 */
public final class ScaleAwareTerrainPlanner {
    public static final int RECONSTRUCTION_RADIUS_METRES = 3_000;
    public static final int MAXIMUM_RESIDUAL_BLOCKS = 4;

    private final EarthAtlasSampler sampler;
    private final WorldProfile profile;
    private final int radiusBlocks;

    public ScaleAwareTerrainPlanner(EarthAtlasSampler sampler) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.profile = sampler.profile();
        this.radiusBlocks = Math.max(
                1,
                (int) Math.ceil((double) RECONSTRUCTION_RADIUS_METRES
                        / profile.horizontalScale().metresPerBlock()));
    }

    public int radiusBlocks() {
        return radiusBlocks;
    }

    /** Resolves one deterministic, coastline-preserving terrain column. */
    public TerrainColumnPlan plan(long blockX, long blockZ) throws IOException {
        EarthAtlasSampler.ColumnSample centerSample = sampler.sample(blockX, blockZ);
        TerrainColumnPlan rawPlan = TerrainColumnPlan.from(profile, centerSample);
        if (!rawPlan.land()
                || rawPlan.dataAvailability() != TerrainColumnPlan.DataAvailability.COMPLETE) {
            return rawPlan;
        }

        long weightedHeight = 0L;
        long totalWeight = 0L;
        for (int offsetX = -radiusBlocks; offsetX <= radiusBlocks; offsetX++) {
            for (int offsetZ = -radiusBlocks; offsetZ <= radiusBlocks; offsetZ++) {
                EarthAtlasSampler.ColumnSample nearby = sampler.sample(
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
            return rawPlan;
        }

        int regionalHeight = Math.toIntExact(Math.round((double) weightedHeight / totalWeight));
        int residual = rawPlan.solidTopY() - regionalHeight;
        int preservedResidual = Math.max(
                -MAXIMUM_RESIDUAL_BLOCKS,
                Math.min(MAXIMUM_RESIDUAL_BLOCKS, residual));
        int generalizedHeight = regionalHeight + preservedResidual;
        generalizedHeight = Math.max(profile.seaLevel(), Math.min(profile.maximumY(), generalizedHeight));

        return new TerrainColumnPlan(
                true,
                generalizedHeight,
                rawPlan.seaLevel(),
                rawPlan.minimumY(),
                rawPlan.maximumY(),
                rawPlan.dataAvailability());
    }

    private int kernelWeight(int offset) {
        return radiusBlocks + 1 - Math.abs(offset);
    }
}
