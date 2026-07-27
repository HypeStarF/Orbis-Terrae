package me.sdmannen.orbis_terrae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;
import me.sdmannen.orbis_terrae.geo.EarthCoordinateMapper;
import me.sdmannen.orbis_terrae.profile.WorldProfiles;
import me.sdmannen.orbis_terrae.worldgen.TerrainColumnPlan;
import me.sdmannen.orbis_terrae.worldgen.atlas.EarthAtlasSampler;
import me.sdmannen.orbis_terrae.worldgen.atlas.OrbisAtlasRuntime;
import me.sdmannen.orbis_terrae.worldgen.validation.TerrainContinuityAudit;
import me.sdmannen.orbis_terrae.worldgen.validation.TerrainPipelineBenchmark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Phase2PerformanceExitAuditTest {
    private static final GeoCoordinate BERGEN = new GeoCoordinate(60.3913, 5.3221);

    @TempDir
    Path temporaryDirectory;

    @Test
    void benchmarksBundledBergenTerrainPlanningAgainstInitialTarget() throws Exception {
        EarthAtlasSampler sampler = sampler("benchmark-game");
        EarthCoordinateMapper.BlockCoordinate projected = new EarthCoordinateMapper(
                WorldProfiles.GLOBAL_SURVIVAL).toBlock(BERGEN);
        int centerBlockX = Math.toIntExact(Math.round(projected.x()));
        int centerBlockZ = Math.toIntExact(Math.round(projected.z()));
        int centerChunkX = Math.floorDiv(centerBlockX, 16);
        int centerChunkZ = Math.floorDiv(centerBlockZ, 16);
        TerrainPipelineBenchmark.BenchmarkConfig configuration =
                new TerrainPipelineBenchmark.BenchmarkConfig(1, 1, 5);

        TerrainPipelineBenchmark.BenchmarkReport first = TerrainPipelineBenchmark.run(
                sampler,
                centerChunkX,
                centerChunkZ,
                configuration);
        TerrainPipelineBenchmark.BenchmarkReport second = TerrainPipelineBenchmark.run(
                sampler,
                centerChunkX,
                centerChunkZ,
                configuration);

        assertEquals(first.workloadFingerprint(), second.workloadFingerprint());
        assertEquals(first.resultFingerprint(), second.resultFingerprint());
        assertEquals(9, first.workloadChunks());
        assertEquals(45, first.measuredChunks());
        assertEquals(11_520L, first.measuredColumns());
        assertTrue(first.p95TargetMet(),
                () -> "Terrain planning p95 exceeded 150 ms: " + first.latency().p95Nanos() + " ns");
        writeReport("TERRAIN_BENCHMARK_REPORT", first.toJson());
    }

    @Test
    void recordsDeterministicTerrainShapeAndChunkBoundaryMetrics() throws Exception {
        EarthAtlasSampler sampler = sampler("audit-game");
        EarthCoordinateMapper.BlockCoordinate projected = new EarthCoordinateMapper(
                WorldProfiles.GLOBAL_SURVIVAL).toBlock(BERGEN);
        int centerBlockX = Math.toIntExact(Math.round(projected.x()));
        int centerBlockZ = Math.toIntExact(Math.round(projected.z()));
        TerrainContinuityAudit.AuditConfig configuration =
                new TerrainContinuityAudit.AuditConfig(48, 24, 8);

        TerrainContinuityAudit.AuditReport first = TerrainContinuityAudit.analyze(
                centerBlockX,
                centerBlockZ,
                configuration,
                (blockX, blockZ) -> plan(sampler, blockX, blockZ));
        TerrainContinuityAudit.AuditReport second = TerrainContinuityAudit.analyze(
                centerBlockX,
                centerBlockZ,
                configuration,
                (blockX, blockZ) -> plan(sampler, blockX, blockZ));

        assertEquals(first.terrainFingerprint(), second.terrainFingerprint());
        assertEquals(first.completeColumns(), second.completeColumns());
        assertEquals(first.steepLandPairs(), second.steepLandPairs());
        assertEquals(first.isolatedPeaks(), second.isolatedPeaks());
        assertTrue(first.completeColumns() > 0);
        assertTrue(first.landColumns() > 0);
        assertTrue(first.oceanColumns() > 0);
        assertTrue(first.landNeighborPairs() > 0);
        assertTrue(first.chunkBoundaryLandPairs() > 0);
        assertTrue(first.interiorLandPairs() > 0);
        writeReport("TERRAIN_CONTINUITY_REPORT", first.toJson());
    }

    private EarthAtlasSampler sampler(String gameDirectoryName) throws IOException {
        return OrbisAtlasRuntime.openBundled(temporaryDirectory.resolve(gameDirectoryName))
                .sampler(WorldProfiles.GLOBAL_SURVIVAL);
    }

    private static TerrainColumnPlan plan(EarthAtlasSampler sampler, int blockX, int blockZ) {
        try {
            return TerrainColumnPlan.from(sampler.profile(), sampler.sample(blockX, blockZ));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void writeReport(String environmentVariable, String json) throws IOException {
        String destination = System.getenv(environmentVariable);
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
}
