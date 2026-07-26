package me.sdmannen.orbis_terrae.atlas.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import me.sdmannen.orbis_terrae.atlas.sampling.ElevationSampler;
import me.sdmannen.orbis_terrae.atlas.sampling.LandMaskSampler;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SyntheticAtlasFixtureTest {
    private static final String FIXTURE_RELATIVE_PATH =
            "atlas/test-fixtures/multi-tile-v1";

    @TempDir
    Path temporaryDirectory;

    @Test
    void checkedInFixtureIsCanonicalAndSampleable() throws Exception {
        Path fixture = repositoryFixture();
        SyntheticAtlasFixture.verify(fixture);

        AtlasDirectory atlas = AtlasDirectory.open(fixture, 8);
        AtlasLayer elevationLayer = atlas.requireElevationLayer("elevation");
        ElevationSampler elevation = new ElevationSampler(atlas, "elevation");
        LandMaskSampler land = new LandMaskSampler(atlas, "land_mask");

        assertEquals(SyntheticAtlasFixture.manifest(), atlas.manifest());
        assertEquals(2, elevationLayer.tileColumns());
        assertEquals(2, elevationLayer.tileRows());
        assertEquals(OptionalInt.of(0), elevation.sampleNearestMetres(3.0, 0.0));
        assertEquals(OptionalDouble.of(165.0), elevation.sampleBilinearMetres(1.5, 1.5));
        assertEquals(OptionalInt.empty(), elevation.sampleNearestMetres(0.0, 3.0));
        assertTrue(land.isLand(3.0, 0.0));
        assertFalse(land.isLand(3.0, 3.0));
        assertTrue(land.isLand(0.0, 3.0));
    }

    @Test
    void writesFixtureDeterministically() throws Exception {
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");

        SyntheticAtlasFixture.write(first);
        SyntheticAtlasFixture.write(second);
        SyntheticAtlasFixture.verify(first);
        SyntheticAtlasFixture.verify(second);

        for (Map.Entry<String, byte[]> entry : SyntheticAtlasFixture.expectedFiles().entrySet()) {
            assertArrayEquals(entry.getValue(), Files.readAllBytes(first.resolve(entry.getKey())));
            assertArrayEquals(
                    Files.readAllBytes(first.resolve(entry.getKey())),
                    Files.readAllBytes(second.resolve(entry.getKey())));
        }
    }

    @Test
    void detectsModifiedMissingAndUnexpectedFiles() throws Exception {
        Path fixture = temporaryDirectory.resolve("modified");
        String tile = "layers/elevation/0/0/0.otat";
        SyntheticAtlasFixture.write(fixture);

        byte[] modified = Files.readAllBytes(fixture.resolve(tile));
        modified[modified.length - 1] ^= 1;
        Files.write(fixture.resolve(tile), modified);
        assertThrows(IOException.class, () -> SyntheticAtlasFixture.verify(fixture));

        SyntheticAtlasFixture.write(fixture);
        Files.delete(fixture.resolve(tile));
        assertThrows(IOException.class, () -> SyntheticAtlasFixture.verify(fixture));

        SyntheticAtlasFixture.write(fixture);
        Files.writeString(fixture.resolve("unexpected.txt"), "unexpected\n");
        assertThrows(IOException.class, () -> SyntheticAtlasFixture.verify(fixture));
    }

    @Test
    void compilerCommandsGenerateAndVerifyFixture() throws Exception {
        Path fixture = temporaryDirectory.resolve("cli-fixture");

        AtlasCompilerCli.main(
                new String[] {"generate-synthetic-fixture", fixture.toString()});
        AtlasCompilerCli.main(
                new String[] {"verify-synthetic-fixture", fixture.toString()});

        assertEquals(SyntheticAtlasFixture.ATLAS_ID, AtlasDirectory.open(fixture).manifest().atlasId());
    }

    @Test
    void rejectsMissingAndNonDirectoryPaths() throws Exception {
        Path missing = temporaryDirectory.resolve("missing");
        Path file = temporaryDirectory.resolve("file");
        Files.writeString(file, "not a directory\n");

        assertThrows(NoSuchFileException.class, () -> SyntheticAtlasFixture.verify(missing));
        assertThrows(NotDirectoryException.class, () -> SyntheticAtlasFixture.verify(file));
        assertThrows(NotDirectoryException.class, () -> SyntheticAtlasFixture.write(file));
    }

    private static Path repositoryFixture() {
        Path[] candidates = {
            Path.of("../..", FIXTURE_RELATIVE_PATH),
            Path.of(FIXTURE_RELATIVE_PATH)
        };
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException("Could not locate repository fixture " + FIXTURE_RELATIVE_PATH);
    }
}
