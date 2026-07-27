package me.sdmannen.orbis_terrae.worldgen.validation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import me.sdmannen.orbis_terrae.worldgen.TerrainColumnPlan;

/** Measures deterministic terrain-shape and chunk-boundary characteristics over a rectangular window. */
public final class TerrainContinuityAudit {
    public static final double MAXIMUM_STEEP_PAIR_RATIO = 0.02;
    private static final int CHUNK_SIZE = 16;

    private TerrainContinuityAudit() {
    }

    public static AuditReport analyze(
            int centerBlockX,
            int centerBlockZ,
            AuditConfig configuration,
            TerrainChunkFingerprint.ColumnPlanner planner) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(planner, "planner");
        int width = configuration.radiusBlocksX() * 2 + 1;
        int depth = configuration.radiusBlocksZ() * 2 + 1;
        TerrainColumnPlan[][] plans = new TerrainColumnPlan[width][depth];
        MessageDigest fingerprint = sha256();

        int completeColumns = 0;
        int landColumns = 0;
        int oceanColumns = 0;
        int incompleteColumns = 0;
        for (int offsetX = -configuration.radiusBlocksX();
                offsetX <= configuration.radiusBlocksX();
                offsetX++) {
            for (int offsetZ = -configuration.radiusBlocksZ();
                    offsetZ <= configuration.radiusBlocksZ();
                    offsetZ++) {
                int blockX = Math.addExact(centerBlockX, offsetX);
                int blockZ = Math.addExact(centerBlockZ, offsetZ);
                TerrainColumnPlan plan = Objects.requireNonNull(
                        planner.plan(blockX, blockZ),
                        "terrainColumnPlan");
                plans[offsetX + configuration.radiusBlocksX()]
                        [offsetZ + configuration.radiusBlocksZ()] = plan;
                updatePlan(fingerprint, blockX, blockZ, plan);
                if (plan.dataAvailability() == TerrainColumnPlan.DataAvailability.COMPLETE) {
                    completeColumns++;
                    if (plan.land()) {
                        landColumns++;
                    } else {
                        oceanColumns++;
                    }
                } else {
                    incompleteColumns++;
                }
            }
        }

        List<Integer> allLandSteps = new ArrayList<>();
        List<Integer> boundaryLandSteps = new ArrayList<>();
        List<Integer> interiorLandSteps = new ArrayList<>();
        int steepLandPairs = 0;
        for (int localX = 0; localX < width; localX++) {
            for (int localZ = 0; localZ < depth; localZ++) {
                TerrainColumnPlan current = plans[localX][localZ];
                int blockX = centerBlockX - configuration.radiusBlocksX() + localX;
                int blockZ = centerBlockZ - configuration.radiusBlocksZ() + localZ;
                if (localX + 1 < width) {
                    int delta = addLandStep(
                            current,
                            plans[localX + 1][localZ],
                            crossesChunkBoundary(blockX, blockX + 1),
                            allLandSteps,
                            boundaryLandSteps,
                            interiorLandSteps);
                    if (delta > configuration.steepStepThresholdBlocks()) {
                        steepLandPairs++;
                    }
                }
                if (localZ + 1 < depth) {
                    int delta = addLandStep(
                            current,
                            plans[localX][localZ + 1],
                            crossesChunkBoundary(blockZ, blockZ + 1),
                            allLandSteps,
                            boundaryLandSteps,
                            interiorLandSteps);
                    if (delta > configuration.steepStepThresholdBlocks()) {
                        steepLandPairs++;
                    }
                }
            }
        }

        int isolatedPeaks = countIsolatedPeaks(plans, configuration.steepStepThresholdBlocks());
        double steepPairRatio = allLandSteps.isEmpty()
                ? 0.0
                : (double) steepLandPairs / allLandSteps.size();
        boolean qualityTargetMet = steepPairRatio <= MAXIMUM_STEEP_PAIR_RATIO && isolatedPeaks == 0;
        return new AuditReport(
                configuration,
                width,
                depth,
                completeColumns,
                landColumns,
                oceanColumns,
                incompleteColumns,
                allLandSteps.size(),
                boundaryLandSteps.size(),
                interiorLandSteps.size(),
                steepLandPairs,
                steepPairRatio,
                isolatedPeaks,
                summarize(allLandSteps),
                summarize(boundaryLandSteps),
                summarize(interiorLandSteps),
                qualityTargetMet,
                HexFormat.of().formatHex(fingerprint.digest()));
    }

    private static int addLandStep(
            TerrainColumnPlan first,
            TerrainColumnPlan second,
            boolean chunkBoundary,
            List<Integer> all,
            List<Integer> boundaries,
            List<Integer> interiors) {
        if (!isCompleteLand(first) || !isCompleteLand(second)) {
            return -1;
        }
        int delta = Math.abs(first.solidTopY() - second.solidTopY());
        all.add(delta);
        if (chunkBoundary) {
            boundaries.add(delta);
        } else {
            interiors.add(delta);
        }
        return delta;
    }

    private static int countIsolatedPeaks(TerrainColumnPlan[][] plans, int threshold) {
        int peaks = 0;
        for (int x = 1; x < plans.length - 1; x++) {
            for (int z = 1; z < plans[x].length - 1; z++) {
                TerrainColumnPlan center = plans[x][z];
                TerrainColumnPlan north = plans[x][z - 1];
                TerrainColumnPlan south = plans[x][z + 1];
                TerrainColumnPlan west = plans[x - 1][z];
                TerrainColumnPlan east = plans[x + 1][z];
                if (!isCompleteLand(center)
                        || !isCompleteLand(north)
                        || !isCompleteLand(south)
                        || !isCompleteLand(west)
                        || !isCompleteLand(east)) {
                    continue;
                }
                int highestNeighbor = Math.max(
                        Math.max(north.solidTopY(), south.solidTopY()),
                        Math.max(west.solidTopY(), east.solidTopY()));
                if (center.solidTopY() - highestNeighbor > threshold) {
                    peaks++;
                }
            }
        }
        return peaks;
    }

    private static boolean isCompleteLand(TerrainColumnPlan plan) {
        return plan.land() && plan.dataAvailability() == TerrainColumnPlan.DataAvailability.COMPLETE;
    }

    private static boolean crossesChunkBoundary(int firstCoordinate, int secondCoordinate) {
        return Math.floorDiv(firstCoordinate, CHUNK_SIZE) != Math.floorDiv(secondCoordinate, CHUNK_SIZE);
    }

    private static StepSummary summarize(List<Integer> values) {
        if (values.isEmpty()) {
            return new StepSummary(0, 0, 0);
        }
        List<Integer> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int p95Index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        long total = sorted.stream().mapToLong(Integer::longValue).sum();
        return new StepSummary(
                sorted.get(Math.min(p95Index, sorted.size() - 1)),
                sorted.getLast(),
                total);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    private static void updatePlan(
            MessageDigest digest,
            int blockX,
            int blockZ,
            TerrainColumnPlan plan) {
        updateInt(digest, blockX);
        updateInt(digest, blockZ);
        digest.update((byte) (plan.land() ? 1 : 0));
        updateInt(digest, plan.solidTopY());
        updateInt(digest, plan.seaLevel());
        digest.update((byte) plan.dataAvailability().ordinal());
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value)
                .array());
    }

    /** Spatial and provisional terrain-quality controls for one audit window. */
    public record AuditConfig(int radiusBlocksX, int radiusBlocksZ, int steepStepThresholdBlocks) {
        public AuditConfig {
            if (radiusBlocksX <= 0 || radiusBlocksZ <= 0) {
                throw new IllegalArgumentException("Audit radii must be positive");
            }
            if (steepStepThresholdBlocks <= 0) {
                throw new IllegalArgumentException("Steep-step threshold must be positive");
            }
        }
    }

    /** Adjacent-column step statistics. */
    public record StepSummary(int p95Blocks, int maximumBlocks, long totalBlocks) {
    }

    /** Deterministic terrain-shape report suitable for CI artifact upload. */
    public record AuditReport(
            AuditConfig configuration,
            int widthBlocks,
            int depthBlocks,
            int completeColumns,
            int landColumns,
            int oceanColumns,
            int incompleteColumns,
            int landNeighborPairs,
            int chunkBoundaryLandPairs,
            int interiorLandPairs,
            int steepLandPairs,
            double steepLandPairRatio,
            int isolatedPeaks,
            StepSummary allLandSteps,
            StepSummary chunkBoundaryLandSteps,
            StepSummary interiorLandSteps,
            boolean terrainQualityTargetMet,
            String terrainFingerprint) {
        public AuditReport {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(allLandSteps, "allLandSteps");
            Objects.requireNonNull(chunkBoundaryLandSteps, "chunkBoundaryLandSteps");
            Objects.requireNonNull(interiorLandSteps, "interiorLandSteps");
            Objects.requireNonNull(terrainFingerprint, "terrainFingerprint");
        }

        public String toJson() {
            return String.format(
                    Locale.ROOT,
                    "{\n"
                            + "  \"radiusBlocksX\": %d,\n"
                            + "  \"radiusBlocksZ\": %d,\n"
                            + "  \"steepStepThresholdBlocks\": %d,\n"
                            + "  \"maximumSteepPairRatio\": %.4f,\n"
                            + "  \"widthBlocks\": %d,\n"
                            + "  \"depthBlocks\": %d,\n"
                            + "  \"completeColumns\": %d,\n"
                            + "  \"landColumns\": %d,\n"
                            + "  \"oceanColumns\": %d,\n"
                            + "  \"incompleteColumns\": %d,\n"
                            + "  \"landNeighborPairs\": %d,\n"
                            + "  \"chunkBoundaryLandPairs\": %d,\n"
                            + "  \"interiorLandPairs\": %d,\n"
                            + "  \"steepLandPairs\": %d,\n"
                            + "  \"steepLandPairRatio\": %.6f,\n"
                            + "  \"isolatedPeaks\": %d,\n"
                            + "  \"allLandP95StepBlocks\": %d,\n"
                            + "  \"allLandMaximumStepBlocks\": %d,\n"
                            + "  \"chunkBoundaryP95StepBlocks\": %d,\n"
                            + "  \"chunkBoundaryMaximumStepBlocks\": %d,\n"
                            + "  \"interiorP95StepBlocks\": %d,\n"
                            + "  \"interiorMaximumStepBlocks\": %d,\n"
                            + "  \"terrainQualityTargetMet\": %s,\n"
                            + "  \"terrainFingerprint\": \"%s\"\n"
                            + "}\n",
                    configuration.radiusBlocksX(),
                    configuration.radiusBlocksZ(),
                    configuration.steepStepThresholdBlocks(),
                    MAXIMUM_STEEP_PAIR_RATIO,
                    widthBlocks,
                    depthBlocks,
                    completeColumns,
                    landColumns,
                    oceanColumns,
                    incompleteColumns,
                    landNeighborPairs,
                    chunkBoundaryLandPairs,
                    interiorLandPairs,
                    steepLandPairs,
                    steepLandPairRatio,
                    isolatedPeaks,
                    allLandSteps.p95Blocks(),
                    allLandSteps.maximumBlocks(),
                    chunkBoundaryLandSteps.p95Blocks(),
                    chunkBoundaryLandSteps.maximumBlocks(),
                    interiorLandSteps.p95Blocks(),
                    interiorLandSteps.maximumBlocks(),
                    terrainQualityTargetMet,
                    terrainFingerprint);
        }
    }
}
