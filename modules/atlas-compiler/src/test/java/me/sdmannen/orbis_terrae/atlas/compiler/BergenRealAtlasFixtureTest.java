package me.sdmannen.orbis_terrae.atlas.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.sampling.ElevationSampler;
import me.sdmannen.orbis_terrae.atlas.sampling.LandMaskSampler;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BergenRealAtlasFixtureTest {
    private static final String FIXTURE_RELATIVE_PATH =
            "atlas/test-fixtures/bergen-real-v1.zip";

    @TempDir
    Path temporaryDirectory;

    @Test
    void opensVerifiedArchiveAndSamplesRealLocations() throws Exception {
        Path atlasRoot = extractRepositoryFixture();
        verifyChecksums(atlasRoot);

        AtlasDirectory atlas = AtlasDirectory.open(atlasRoot, 16);
        AtlasLayer elevationLayer = atlas.requireElevationLayer("elevation");
        AtlasLayer landLayer = atlas.requireLandMaskLayer("land_mask");
        ElevationSampler elevation = new ElevationSampler(atlas, "elevation");
        LandMaskSampler land = new LandMaskSampler(atlas, "land_mask");

        assertEquals("bergen-real-v1", atlas.manifest().atlasId());
        assertEquals(new GeoBounds(4.75, 60.2, 5.75, 60.8), atlas.manifest().bounds());
        assertEquals(4, elevationLayer.tileColumns());
        assertEquals(4, elevationLayer.tileRows());
        assertEquals(4, landLayer.tileColumns());
        assertEquals(4, landLayer.tileRows());

        assertEquals(OptionalInt.of(15), elevation.sampleNearestMetres(60.3913, 5.3221));
        assertEquals(OptionalInt.of(484), elevation.sampleNearestMetres(60.3775, 5.3791));
        assertEquals(OptionalInt.of(369), elevation.sampleNearestMetres(60.3988, 5.3456));
        assertEquals(OptionalInt.of(0), elevation.sampleNearestMetres(60.45, 4.8));

        assertTrue(land.isLand(60.3913, 5.3221));
        assertTrue(land.isLand(60.3775, 5.3791));
        assertTrue(land.isLand(60.35, 5.05));
        assertTrue(land.isLand(60.45, 5.15));
        assertFalse(land.isLand(60.45, 4.8));
        assertFalse(land.isLand(60.42, 5.25));
        assertFalse(land.isLand(60.25, 5.28));
    }

    @Test
    void interpolatesAcrossFourRealElevationTiles() throws Exception {
        Path atlasRoot = extractRepositoryFixture();
        AtlasDirectory atlas = AtlasDirectory.open(atlasRoot, 8);
        ElevationSampler elevation = new ElevationSampler(atlas, "elevation");

        OptionalDouble sampled = elevation.sampleBilinearMetres(
                60.34941176470588,
                5.500980392156863);

        assertTrue(sampled.isPresent());
        assertEquals(446.0, sampled.getAsDouble(), 1.0e-9);
        assertEquals(4, atlas.cacheStats().misses());
    }

    @Test
    void preservesRequiredAttribution() throws Exception {
        Path atlasRoot = extractRepositoryFixture();
        String attribution = Files.readString(
                atlasRoot.resolve("ATTRIBUTION.md"),
                StandardCharsets.UTF_8);

        assertTrue(attribution.contains("Copernicus WorldDEM-90"));
        assertTrue(attribution.contains("European Union and ESA"));
        assertTrue(attribution.contains("Natural Earth"));
        assertTrue(attribution.contains("public domain"));
    }

    private Path extractRepositoryFixture() throws IOException {
        Path archive = repositoryFixture();
        Path output = temporaryDirectory.resolve("atlas-" + System.nanoTime());
        Files.createDirectories(output);
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path target = output.resolve(entry.getName()).normalize();
                if (!target.startsWith(output)) {
                    throw new IOException("Unsafe fixture archive entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target);
                }
            }
        }
        return output;
    }

    private static void verifyChecksums(Path atlasRoot) throws Exception {
        Path checksums = atlasRoot.resolve("fixture-checksums.sha256");
        for (String line : Files.readAllLines(checksums, StandardCharsets.US_ASCII)) {
            String[] fields = line.split("  ", 2);
            assertEquals(2, fields.length);
            Path file = atlasRoot.resolve(fields[1]).normalize();
            assertTrue(file.startsWith(atlasRoot));
            assertTrue(Files.isRegularFile(file));
            assertEquals(fields[0], sha256(file));
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path repositoryFixture() {
        Path[] candidates = {
            Path.of("../..", FIXTURE_RELATIVE_PATH),
            Path.of(FIXTURE_RELATIVE_PATH)
        };
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException("Could not locate repository fixture " + FIXTURE_RELATIVE_PATH);
    }
}
