package me.sdmannen.orbis_terrae.atlas.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;
import me.sdmannen.orbis_terrae.atlas.sampling.ElevationSampler;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtlasRuntimeBenchmarkTest {
    private static final int GRID_SIZE = 64;
    private static final int TILE_SIZE = 8;
    private static final int CACHE_TILES = 4;
    private static final int PARALLEL_WORKERS = 4;
    private static final int PARALLEL_OPERATIONS_PER_WORKER = 1_000;

    private final AtlasTileWriter tileWriter = new AtlasTileWriter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void recordsLatencyAndEnforcesCacheAndWarmLookupTargets() throws Exception {
        Path atlasRoot = createAtlas("benchmark-atlas");
        AtlasRuntimeBenchmark.BenchmarkConfig config =
                new AtlasRuntimeBenchmark.BenchmarkConfig(64, 5_000, 2_000, 0x0b15e5L, CACHE_TILES);

        AtlasRuntimeBenchmark.BenchmarkReport first = AtlasRuntimeBenchmark.run(
                AtlasDirectory.open(atlasRoot, CACHE_TILES), "elevation", config);
        AtlasRuntimeBenchmark.BenchmarkReport second = AtlasRuntimeBenchmark.run(
                AtlasDirectory.open(atlasRoot, CACHE_TILES), "elevation", config);

        assertEquals(first.workloadFingerprint(), second.workloadFingerprint());
        assertEquals(first.resultFingerprint(), second.resultFingerprint());
        assertTrue(first.warmP95TargetMet(),
                () -> "Warm p95 exceeded 2 ms: " + first.warmNearest().p95Nanos() + " ns");
        assertTrue(first.cacheHits() > 0);
        assertTrue(first.cacheMisses() > 0);
        assertTrue(first.maximumObservedCacheSize() <= CACHE_TILES);
        assertTrue(first.finalCacheSize() <= CACHE_TILES);
        assertEquals(512L, first.maximumDecodedElevationPayloadBytes());

        JsonNode report = objectMapper.readTree(first.toJson());
        assertEquals("phase1-performance-fixture", report.path("atlasId").asText());
        assertEquals(first.resultFingerprint(), report.path("resultFingerprint").asText());
        assertTrue(report.path("warmP95TargetMet").asBoolean());
        writeCiReport(first.toJson());
    }

    @Test
    void sharedReaderRemainsDeterministicAndBoundedUnderParallelSampling() throws Exception {
        Path atlasRoot = createAtlas("parallel-atlas");

        ParallelResult first = runParallel(atlasRoot);
        ParallelResult second = runParallel(atlasRoot);

        assertEquals(first.checksum(), second.checksum());
        assertTrue(first.maximumCacheSize() <= CACHE_TILES);
        assertTrue(second.maximumCacheSize() <= CACHE_TILES);
    }

    private ParallelResult runParallel(Path atlasRoot)
            throws IOException, InterruptedException, ExecutionException {
        AtlasDirectory atlas = AtlasDirectory.open(atlasRoot, CACHE_TILES);
        ElevationSampler sampler = new ElevationSampler(atlas, "elevation");
        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_WORKERS);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int worker = 0; worker < PARALLEL_WORKERS; worker++) {
                int workerIndex = worker;
                tasks.add(() -> sampleWorker(sampler, workerIndex));
            }
            long checksum = 0L;
            for (Future<Long> result : executor.invokeAll(tasks)) {
                checksum = mix(checksum ^ result.get());
            }
            return new ParallelResult(checksum, atlas.cacheStats().size());
        } finally {
            executor.shutdownNow();
        }
    }

    private static long sampleWorker(ElevationSampler sampler, int worker) throws IOException {
        SplittableRandom random = new SplittableRandom(0x51a7c0deL + worker);
        long checksum = 0L;
        for (int operation = 0; operation < PARALLEL_OPERATIONS_PER_WORKER; operation++) {
            double latitude = random.nextDouble(0.0, GRID_SIZE - 1.0);
            double longitude = random.nextDouble(0.0, GRID_SIZE - 1.0);
            int elevation = sampler.sampleNearestMetres(latitude, longitude).orElseThrow();
            checksum = mix(checksum
                    ^ elevation
                    ^ Long.rotateLeft(Double.doubleToLongBits(latitude), operation & 63)
                    ^ Long.rotateLeft(Double.doubleToLongBits(longitude), (operation + 19) & 63));
        }
        return checksum;
    }

    private Path createAtlas(String directoryName) throws IOException {
        Path root = temporaryDirectory.resolve(directoryName);
        Files.createDirectories(root);
        AtlasManifestJson.write(root.resolve(AtlasDirectory.MANIFEST_FILE_NAME), manifest());
        int tileCount = GRID_SIZE / TILE_SIZE;
        for (int tileY = 0; tileY < tileCount; tileY++) {
            for (int tileX = 0; tileX < tileCount; tileX++) {
                writeElevationTile(root, tileX, tileY);
            }
        }
        return root;
    }

    private void writeElevationTile(Path root, int tileX, int tileY) throws IOException {
        short[] elevations = new short[TILE_SIZE * TILE_SIZE];
        for (int localY = 0; localY < TILE_SIZE; localY++) {
            for (int localX = 0; localX < TILE_SIZE; localX++) {
                int sampleX = tileX * TILE_SIZE + localX;
                int sampleY = tileY * TILE_SIZE + localY;
                elevations[localY * TILE_SIZE + localX] =
                        (short) (sampleY * GRID_SIZE + sampleX);
            }
        }
        Path path = root.resolve("layers/elevation/0/" + tileX + "/" + tileY + ".otat");
        Files.createDirectories(path.getParent());
        Files.write(path, tileWriter.encodeElevation(TILE_SIZE, elevations));
    }

    private static AtlasManifest manifest() {
        return new AtlasManifest(
                AtlasManifest.CURRENT_SCHEMA_VERSION,
                "phase1-performance-fixture",
                "1.0.0",
                "0.1.0-SNAPSHOT",
                AtlasManifest.Projection.EQUIRECTANGULAR,
                new GeoBounds(0.0, 0.0, GRID_SIZE - 1.0, GRID_SIZE - 1.0),
                List.of(new AtlasManifest.Layer(
                        "elevation",
                        AtlasManifest.LayerType.ELEVATION,
                        1,
                        AtlasManifest.Encoding.SIGNED_INT16_LE,
                        TILE_SIZE,
                        0,
                        GRID_SIZE,
                        GRID_SIZE,
                        (int) ElevationTile.NO_DATA,
                        "layers/elevation/{z}/{x}/{y}.otat")),
                List.of(new AtlasManifest.Provenance(
                        "phase1-performance-fixture",
                        "Synthetic performance fixture",
                        "1",
                        "Orbis Terrae project test fixture",
                        "Generated for repeatable runtime measurements",
                        "https://orbis-terrae.invalid/fixtures/performance",
                        "2026-07-27",
                        List.of("Generated deterministic elevation tiles"))));
    }

    private static void writeCiReport(String json) throws IOException {
        String destination = System.getenv("ATLAS_BENCHMARK_REPORT");
        if (destination == null || destination.isBlank()) {
            return;
        }
        Path path = Path.of(destination);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record ParallelResult(long checksum, int maximumCacheSize) {
    }
}
