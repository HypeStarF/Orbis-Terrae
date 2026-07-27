package me.sdmannen.orbis_terrae.worldgen.validation;

import java.io.UncheckedIOException;
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
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;

/** Portable benchmark for the atlas-backed terrain-column planning pipeline. */
public final class TerrainPipelineBenchmark {
    public static final long TARGET_P95_NANOS = 150_000_000L;
    private static final int CHUNK_SIZE = 16;
    private static final int COLUMNS_PER_CHUNK = CHUNK_SIZE * CHUNK_SIZE;

    private TerrainPipelineBenchmark() {
    }

    public static BenchmarkReport run(
            EarthAtlasSampler sampler,
            int centerChunkX,
            int centerChunkZ,
            BenchmarkConfig configuration) {
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(configuration, "configuration");
        List<ChunkCoordinate> workload = workload(centerChunkX, centerChunkZ, configuration.chunkRadius());
        TerrainChunkFingerprint.ColumnPlanner planner = (blockX, blockZ) -> {
            try {
                return TerrainColumnPlan.from(sampler.profile(), sampler.sample(blockX, blockZ));
            } catch (java.io.IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };

        for (int pass = 0; pass < configuration.warmupPasses(); pass++) {
            for (ChunkCoordinate chunk : workload) {
                TerrainChunkFingerprint.compute(chunk.x(), chunk.z(), planner);
            }
        }

        List<Long> latencies = new ArrayList<>();
        MessageDigest resultDigest = sha256();
        for (int pass = 0; pass < configuration.measuredPasses(); pass++) {
            for (ChunkCoordinate chunk : workload) {
                long started = System.nanoTime();
                String fingerprint = TerrainChunkFingerprint.compute(chunk.x(), chunk.z(), planner);
                long elapsed = System.nanoTime() - started;
                latencies.add(elapsed);
                updateInt(resultDigest, pass);
                updateInt(resultDigest, chunk.x());
                updateInt(resultDigest, chunk.z());
                resultDigest.update(fingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
        }

        long totalNanos = latencies.stream().mapToLong(Long::longValue).sum();
        int measuredChunks = latencies.size();
        long measuredColumns = Math.multiplyExact((long) measuredChunks, COLUMNS_PER_CHUNK);
        double columnsPerSecond = totalNanos == 0L
                ? Double.POSITIVE_INFINITY
                : measuredColumns * 1_000_000_000.0 / totalNanos;
        LatencySummary latency = summarize(latencies);
        return new BenchmarkReport(
                configuration,
                workload.size(),
                measuredChunks,
                measuredColumns,
                workloadFingerprint(workload, configuration),
                HexFormat.of().formatHex(resultDigest.digest()),
                latency,
                columnsPerSecond,
                latency.p95Nanos() <= TARGET_P95_NANOS,
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"));
    }

    private static List<ChunkCoordinate> workload(int centerX, int centerZ, int radius) {
        List<ChunkCoordinate> chunks = new ArrayList<>();
        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                chunks.add(new ChunkCoordinate(chunkX, chunkZ));
            }
        }
        return List.copyOf(chunks);
    }

    private static String workloadFingerprint(
            List<ChunkCoordinate> workload,
            BenchmarkConfig configuration) {
        MessageDigest digest = sha256();
        updateInt(digest, configuration.chunkRadius());
        updateInt(digest, configuration.warmupPasses());
        updateInt(digest, configuration.measuredPasses());
        for (ChunkCoordinate chunk : workload) {
            updateInt(digest, chunk.x());
            updateInt(digest, chunk.z());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static LatencySummary summarize(List<Long> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("At least one measured chunk is required");
        }
        List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        return new LatencySummary(
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                sorted.getLast(),
                sorted.stream().mapToLong(Long::longValue).sum());
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value)
                .array());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Repeatable workload controls for the benchmark. */
    public record BenchmarkConfig(int chunkRadius, int warmupPasses, int measuredPasses) {
        public BenchmarkConfig {
            if (chunkRadius < 0 || chunkRadius > 16) {
                throw new IllegalArgumentException("chunkRadius must be between 0 and 16");
            }
            if (warmupPasses < 0) {
                throw new IllegalArgumentException("warmupPasses must not be negative");
            }
            if (measuredPasses <= 0) {
                throw new IllegalArgumentException("measuredPasses must be positive");
            }
        }
    }

    /** Percentile and aggregate latency values in nanoseconds. */
    public record LatencySummary(
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            long maximumNanos,
            long totalNanos) {
    }

    /** Machine-labelled benchmark report suitable for CI artifact upload. */
    public record BenchmarkReport(
            BenchmarkConfig configuration,
            int workloadChunks,
            int measuredChunks,
            long measuredColumns,
            String workloadFingerprint,
            String resultFingerprint,
            LatencySummary latency,
            double columnsPerSecond,
            boolean p95TargetMet,
            String javaVersion,
            String operatingSystem,
            String architecture) {
        public BenchmarkReport {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(workloadFingerprint, "workloadFingerprint");
            Objects.requireNonNull(resultFingerprint, "resultFingerprint");
            Objects.requireNonNull(latency, "latency");
            Objects.requireNonNull(javaVersion, "javaVersion");
            Objects.requireNonNull(operatingSystem, "operatingSystem");
            Objects.requireNonNull(architecture, "architecture");
        }

        public String toJson() {
            return String.format(
                    Locale.ROOT,
                    "{\n"
                            + "  \"chunkRadius\": %d,\n"
                            + "  \"warmupPasses\": %d,\n"
                            + "  \"measuredPasses\": %d,\n"
                            + "  \"workloadChunks\": %d,\n"
                            + "  \"measuredChunks\": %d,\n"
                            + "  \"measuredColumns\": %d,\n"
                            + "  \"workloadFingerprint\": \"%s\",\n"
                            + "  \"resultFingerprint\": \"%s\",\n"
                            + "  \"p50Nanos\": %d,\n"
                            + "  \"p95Nanos\": %d,\n"
                            + "  \"p99Nanos\": %d,\n"
                            + "  \"maximumNanos\": %d,\n"
                            + "  \"totalNanos\": %d,\n"
                            + "  \"columnsPerSecond\": %.3f,\n"
                            + "  \"p95TargetNanos\": %d,\n"
                            + "  \"p95TargetMet\": %s,\n"
                            + "  \"javaVersion\": \"%s\",\n"
                            + "  \"operatingSystem\": \"%s\",\n"
                            + "  \"architecture\": \"%s\"\n"
                            + "}\n",
                    configuration.chunkRadius(),
                    configuration.warmupPasses(),
                    configuration.measuredPasses(),
                    workloadChunks,
                    measuredChunks,
                    measuredColumns,
                    workloadFingerprint,
                    resultFingerprint,
                    latency.p50Nanos(),
                    latency.p95Nanos(),
                    latency.p99Nanos(),
                    latency.maximumNanos(),
                    latency.totalNanos(),
                    columnsPerSecond,
                    TARGET_P95_NANOS,
                    p95TargetMet,
                    escape(javaVersion),
                    escape(operatingSystem),
                    escape(architecture));
        }
    }

    private record ChunkCoordinate(int x, int z) {
    }
}
