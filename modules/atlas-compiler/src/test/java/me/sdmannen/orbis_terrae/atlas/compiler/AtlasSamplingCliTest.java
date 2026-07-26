package me.sdmannen.orbis_terrae.atlas.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtlasSamplingCliTest {
    private final AtlasTileWriter tileWriter = new AtlasTileWriter();

    @TempDir
    Path temporaryDirectory;

    @Test
    void samplesElevationWithNearestAndBilinearModes() throws Exception {
        Path atlas = createAtlas("elevation-cli-atlas", new short[] {0, 10, 20, 30});

        assertEquals(
                "elevation_metres=0\n",
                runCommand(
                        "sample-elevation",
                        atlas.toString(),
                        "elevation",
                        "1.0",
                        "0.0",
                        "nearest"));
        assertEquals(
                "elevation_metres=15.0\n",
                runCommand(
                        "sample-elevation",
                        atlas.toString(),
                        "elevation",
                        "0.5",
                        "0.5",
                        "bilinear"));
    }

    @Test
    void reportsNoDataAndLandClassification() throws Exception {
        Path atlas = createAtlas(
                "classification-cli-atlas",
                new short[] {ElevationTile.NO_DATA, 10, 20, 30});

        assertEquals(
                "elevation_metres=no-data\n",
                runCommand(
                        "sample-elevation",
                        atlas.toString(),
                        "elevation",
                        "1.0",
                        "0.0",
                        "nearest"));
        assertEquals(
                "land=true\n",
                runCommand(
                        "sample-land",
                        atlas.toString(),
                        "land_mask",
                        "1.0",
                        "0.0"));
        assertEquals(
                "land=false\n",
                runCommand(
                        "sample-land",
                        atlas.toString(),
                        "land_mask",
                        "1.0",
                        "1.0"));
    }

    @Test
    void rejectsInvalidSamplingModeAndNonFiniteCoordinates() throws Exception {
        Path atlas = createAtlas("invalid-cli-atlas", new short[] {0, 10, 20, 30});

        assertThrows(
                IllegalArgumentException.class,
                () -> AtlasCompilerCli.main(new String[] {
                    "sample-elevation",
                    atlas.toString(),
                    "elevation",
                    "0.5",
                    "0.5",
                    "cubic"
                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> AtlasCompilerCli.main(new String[] {
                    "sample-land",
                    atlas.toString(),
                    "land_mask",
                    "NaN",
                    "0.5"
                }));
    }

    private String runCommand(String... args) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            AtlasCompilerCli.main(args);
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private Path createAtlas(String directoryName, short[] elevations) throws IOException {
        Path root = temporaryDirectory.resolve(directoryName);
        Files.createDirectories(root);
        AtlasManifestJson.write(root.resolve(AtlasDirectory.MANIFEST_FILE_NAME), manifest());
        writeTile(
                root.resolve("layers/elevation/0/0/0.otat"),
                tileWriter.encodeElevation(2, elevations));
        BitSet land = new BitSet(4);
        land.set(0);
        land.set(3);
        writeTile(
                root.resolve("layers/land-mask/0/0/0.otat"),
                tileWriter.encodeLandMask(2, land));
        return root;
    }

    private static void writeTile(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static AtlasManifest manifest() {
        return new AtlasManifest(
                AtlasManifest.CURRENT_SCHEMA_VERSION,
                "phase1-sampling-cli-fixture",
                "1.0.0",
                "0.1.0-SNAPSHOT",
                AtlasManifest.Projection.EQUIRECTANGULAR,
                new GeoBounds(0.0, 0.0, 1.0, 1.0),
                List.of(
                        new AtlasManifest.Layer(
                                "elevation",
                                AtlasManifest.LayerType.ELEVATION,
                                1,
                                AtlasManifest.Encoding.SIGNED_INT16_LE,
                                2,
                                0,
                                2,
                                2,
                                (int) ElevationTile.NO_DATA,
                                "layers/elevation/{z}/{x}/{y}.otat"),
                        new AtlasManifest.Layer(
                                "land_mask",
                                AtlasManifest.LayerType.LAND_MASK,
                                1,
                                AtlasManifest.Encoding.PACKED_BITSET_LSB0,
                                2,
                                0,
                                2,
                                2,
                                null,
                                "layers/land-mask/{z}/{x}/{y}.otat")),
                List.of(new AtlasManifest.Provenance(
                        "synthetic-sampling-cli-fixture",
                        "Synthetic geographic sampling CLI fixture",
                        "1",
                        "Orbis Terrae project test fixture",
                        "Generated for command-line sampling tests",
                        "https://orbis-terrae.invalid/fixtures/geographic-sampling-cli",
                        "2026-07-26",
                        List.of("Generated one elevation and one land-mask tile"))));
    }
}
