package me.sdmannen.orbis_terrae.atlas.benchmark;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.SplittableRandom;
import me.sdmannen.orbis_terrae.atlas.cache.BoundedTileCache;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.sampling.ElevationSampler;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;

/** Measures cold, warm, and deterministic random elevation sampling for one atlas directory. */
public final class AtlasRuntimeBenchmark {
    public static final long WARM_P95_TARGET_NANOS = 2_000_000L;

    private AtlasRuntimeBenchmark() {
    }

    public static BenchmarkReport run(
            AtlasDirectory atlas,
            String elevationLayerId,
            BenchmarkConfig config) throws IOException {
        Objects.requireNonNull(atlas, "atlas");
        Objects.requireNonNull(elevationLayerId, "elevationLayerId");
        Objects.requireNonNull(config, "config");

        AtlasLayer layer = atlas.requireElevationLayer(elevationLayerId);
        ElevationSampler sampler = new ElevationSampler(atlas, elevationLayerId);
        GeoBounds bounds = atlas.manifest().bounds();
        Workload coldWorkload = Workload.random(
                bounds, config.coldOperations(), config.seed() ^ 0x13579bdf2468ace0L);
        Workload randomWorkload = Workload.random(
                bounds, config.randomOperations(), config.seed() ^ 0x0f1e2d3c4b5a6978L);
        Workload warmWorkload = Workload.hotspot(
                bounds, config.warmOperations(), config.seed() ^ 0x55aa55aa55aa55aaL);

        Measurement cold = measureCold(atlas, sampler, coldWorkload);
        atlas.clearCache();
        Measurement warm = measure(atlas, sampler, warmWorkload, true);
        atlas.clearCache();
        Measurement random = measure(atlas, sampler, randomWorkload, false);
        BoundedTileCache.CacheStats cache = atlas.cacheStats();

        long maximumDecodedElevationPayloadBytes = Math.multiplyExact(
                config.configuredCacheTiles(),
                Math.multiplyExact(
                        Math.multiplyExact((long) layer.tileSize(), layer.tileSize()),
                        Short.BYTES));
        String workloadFingerprint = Long.toUnsignedString(mix(
                coldWorkload.fingerprint()
                        ^ Long.rotateLeft(warmWorkload.fingerprint(), 17)
                        ^ Long.rotateLeft(randomWorkload.fingerprint(), 39)),
                16);
        String resultFingerprint = Long.toUnsignedString(mix(
                cold.resultChecksum()
                        ^ Long.rotateLeft(warm.resultChecksum(), 17)
                        ^ Long.rotateLeft(random.resultChecksum(), 39)),
                16);

        return new BenchmarkReport(
                atlas.manifest().atlasId(),
                elevationLayerId,
                config,
                workloadFingerprint,
                resultFingerprint,
                cold.latency(),
                warm.latency(),
                random.latency(),
                warm.latency().p95Nanos() <= WARM_P95_TARGET_NANOS,
                cache.hits(),
                cache.misses(),
                cache.size(),
                Math.max(cold.maximumObservedCacheSize(),
                        Math.max(warm.maximumObservedCacheSize(), random.maximumObservedCacheSize())),
                maximumDecodedElevationPayloadBytes);
    }

    private static Measurement measureCold(
            AtlasDirectory atlas,
            ElevationSampler sampler,
            Workload workload) throws IOException {
        long[] durations = new long[workload.size()];
        long resultChecksum = 0L;
        int maximumObservedCacheSize = 0;
        for (int index = 0; index < workload.size(); index++) {
            atlas.clearCache();
            long started = System.nanoTime();
            OptionalInt elevation = sampler.sampleNearestMetres(
                    workload.latitude(index), workload.longitude(index));
            durations[index] = System.nanoTime() - started;
            resultChecksum = accumulate(resultChecksum, index, elevation);
            maximumObservedCacheSize = Math.max(
                    maximumObservedCacheSize, atlas.cacheStats().size());
        }
        return new Measurement(
                LatencyStats.from(durations), resultChecksum, maximumObservedCacheSize);
    }

    private static Measurement measure(
            AtlasDirectory atlas,
            ElevationSampler sampler,
            Workload workload,
            boolean preloadFirstCoordinate) throws IOException {
        if (preloadFirstCoordinate && workload.size() > 0) {
            sampler.sampleNearestMetres(workload.latitude(0), workload.longitude(0));
        }
        long[] durations = new long[workload.size()];
        long resultChecksum = 0L;
        int maximumObservedCacheSize = atlas.cacheStats().size();
        for (int index = 0; index < workload.size(); index++) {
            long started = System.nanoTime();
            OptionalInt elevation = sampler.sampleNearestMetres(
                    workload.latitude(index), workload.longitude(index));
            durations[index] = System.nanoTime() - started;
            resultChecksum = accumulate(resultChecksum, index, elevation);
            maximumObservedCacheSize = Math.max(
                    maximumObservedCacheSize, atlas.cacheStats().size());
        }
        return new Measurement(
                LatencyStats.from(durations), resultChecksum, maximumObservedCacheSize);
    }

    private static long accumulate(long checksum, int index, OptionalInt elevation) {
        long value = elevation.isPresent() ? elevation.getAsInt() : 0x7fff_ffffL;
        return mix(checksum ^ Long.rotateLeft(value + index * 0x9e3779b97f4a7c15L, index & 63));
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public record BenchmarkConfig(
            int coldOperations,
            int warmOperations,
            int randomOperations,
            long seed,
            int configuredCacheTiles) {
        public BenchmarkConfig {
            requirePositive(coldOperations, "coldOperations");
            requirePositive(warmOperations, "warmOperations");
            requirePositive(randomOperations, "randomOperations");
            requirePositive(configuredCacheTiles, "configuredCacheTiles");
        }

        private static void requirePositive(int value, String name) {
            if (value < 1) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }

    public record BenchmarkReport(
            String atlasId,
            String elevationLayerId,
            BenchmarkConfig config,
            String workloadFingerprint,
            String resultFingerprint,
            LatencyStats coldNearest,
            LatencyStats warmNearest,
            LatencyStats randomNearest,
            boolean warmP95TargetMet,
            long cacheHits,
            long cacheMisses,
            int finalCacheSize,
            int maximumObservedCacheSize,
            long maximumDecodedElevationPayloadBytes) {
        public BenchmarkReport {
            Objects.requireNonNull(atlasId, "atlasId");
            Objects.requireNonNull(elevationLayerId, "elevationLayerId");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(workloadFingerprint, "workloadFingerprint");
            Objects.requireNonNull(resultFingerprint, "resultFingerprint");
            Objects.requireNonNull(coldNearest, "coldNearest");
            Objects.requireNonNull(warmNearest, "warmNearest");
            Objects.requireNonNull(randomNearest, "randomNearest");
        }

        public String toJson() {
            StringBuilder output = new StringBuilder(1_024);
            output.append("{\n");
            appendString(output, "atlasId", atlasId, true);
            appendString(output, "elevationLayerId", elevationLayerId, true);
            appendString(output, "javaVersion", System.getProperty("java.version"), true);
            appendString(output, "operatingSystem", System.getProperty("os.name"), true);
            output.append("  \"config\": {")
                    .append("\"coldOperations\": ").append(config.coldOperations()).append(", ")
                    .append("\"warmOperations\": ").append(config.warmOperations()).append(", ")
                    .append("\"randomOperations\": ").append(config.randomOperations()).append(", ")
                    .append("\"seed\": ").append(config.seed()).append(", ")
                    .append("\"configuredCacheTiles\": ")
                    .append(config.configuredCacheTiles()).append("},\n");
            appendString(output, "workloadFingerprint", workloadFingerprint, true);
            appendString(output, "resultFingerprint", resultFingerprint, true);
            appendLatency(output, "coldNearest", coldNearest, true);
            appendLatency(output, "warmNearest", warmNearest, true);
            appendLatency(output, "randomNearest", randomNearest, true);
            output.append("  \"warmP95TargetNanos\": ")
                    .append(WARM_P95_TARGET_NANOS).append(",\n");
            output.append("  \"warmP95TargetMet\": ").append(warmP95TargetMet).append(",\n");
            output.append("  \"cacheHits\": ").append(cacheHits).append(",\n");
            output.append("  \"cacheMisses\": ").append(cacheMisses).append(",\n");
            output.append("  \"finalCacheSize\": ").append(finalCacheSize).append(",\n");
            output.append("  \"maximumObservedCacheSize\": ")
                    .append(maximumObservedCacheSize).append(",\n");
            output.append("  \"maximumDecodedElevationPayloadBytes\": ")
                    .append(maximumDecodedElevationPayloadBytes).append("\n");
            output.append("}\n");
            return output.toString();
        }

        private static void appendLatency(
                StringBuilder output,
                String name,
                LatencyStats latency,
                boolean comma) {
            output.append("  \"").append(name).append("\": {")
                    .append("\"count\": ").append(latency.count()).append(", ")
                    .append("\"minimumNanos\": ").append(latency.minimumNanos()).append(", ")
                    .append("\"p50Nanos\": ").append(latency.p50Nanos()).append(", ")
                    .append("\"p95Nanos\": ").append(latency.p95Nanos()).append(", ")
                    .append("\"p99Nanos\": ").append(latency.p99Nanos()).append(", ")
                    .append("\"maximumNanos\": ").append(latency.maximumNanos()).append(", ")
                    .append("\"meanNanos\": ").append(latency.meanNanos()).append("}")
                    .append(comma ? ",\n" : "\n");
        }

        private static void appendString(
                StringBuilder output,
                String name,
                String value,
                boolean comma) {
            output.append("  \"").append(name).append("\": \"")
                    .append(escape(value)).append("\"")
                    .append(comma ? ",\n" : "\n");
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    public record LatencyStats(
            int count,
            long minimumNanos,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            long maximumNanos,
            double meanNanos) {
        static LatencyStats from(long[] measurements) {
            if (measurements.length == 0) {
                throw new IllegalArgumentException("Latency measurements must not be empty");
            }
            long[] sorted = measurements.clone();
            Arrays.sort(sorted);
            long total = 0L;
            for (long measurement : sorted) {
                total = Math.addExact(total, measurement);
            }
            return new LatencyStats(
                    sorted.length,
                    sorted[0],
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95),
                    percentile(sorted, 0.99),
                    sorted[sorted.length - 1],
                    (double) total / sorted.length);
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1);
            return sorted[index];
        }
    }

    private record Measurement(
            LatencyStats latency,
            long resultChecksum,
            int maximumObservedCacheSize) {
    }

    private record Workload(double[] latitudes, double[] longitudes, long fingerprint) {
        static Workload random(GeoBounds bounds, int count, long seed) {
            SplittableRandom random = new SplittableRandom(seed);
            double[] latitudes = new double[count];
            double[] longitudes = new double[count];
            long fingerprint = 0L;
            for (int index = 0; index < count; index++) {
                latitudes[index] = interpolate(bounds.south(), bounds.north(), random.nextDouble());
                longitudes[index] = interpolate(bounds.west(), bounds.east(), random.nextDouble());
                fingerprint = mix(fingerprint
                        ^ Double.doubleToLongBits(latitudes[index])
                        ^ Long.rotateLeft(Double.doubleToLongBits(longitudes[index]), 23));
            }
            return new Workload(latitudes, longitudes, fingerprint);
        }

        static Workload hotspot(GeoBounds bounds, int count, long seed) {
            SplittableRandom random = new SplittableRandom(seed);
            double centreLatitude = (bounds.south() + bounds.north()) * 0.5;
            double centreLongitude = (bounds.west() + bounds.east()) * 0.5;
            double latitudeRadius = (bounds.north() - bounds.south()) / 10_000.0;
            double longitudeRadius = (bounds.east() - bounds.west()) / 10_000.0;
            double[] latitudes = new double[count];
            double[] longitudes = new double[count];
            long fingerprint = 0L;
            for (int index = 0; index < count; index++) {
                latitudes[index] = centreLatitude
                        + (random.nextDouble() * 2.0 - 1.0) * latitudeRadius;
                longitudes[index] = centreLongitude
                        + (random.nextDouble() * 2.0 - 1.0) * longitudeRadius;
                fingerprint = mix(fingerprint
                        ^ Double.doubleToLongBits(latitudes[index])
                        ^ Long.rotateLeft(Double.doubleToLongBits(longitudes[index]), 23));
            }
            return new Workload(latitudes, longitudes, fingerprint);
        }

        int size() {
            return latitudes.length;
        }

        double latitude(int index) {
            return latitudes[index];
        }

        double longitude(int index) {
            return longitudes[index];
        }

        private static double interpolate(double minimum, double maximum, double fraction) {
            return minimum + (maximum - minimum) * fraction;
        }
    }
}
