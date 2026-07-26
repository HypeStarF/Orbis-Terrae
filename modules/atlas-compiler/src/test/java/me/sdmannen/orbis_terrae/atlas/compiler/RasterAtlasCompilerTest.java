package me.sdmannen.orbis_terrae.atlas.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.format.LandMaskTile;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;
import me.sdmannen.orbis_terrae.atlas.sampling.ElevationSampler;
import me.sdmannen.orbis_terrae.atlas.sampling.LandMaskSampler;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RasterAtlasCompilerTest {
    private static final short[] ELEVATION = {
        0, 1, 2, 3, 4,
        5, 6, 7, 8, 9,
        10, 11, 12, 13, 14
    };
    private static final byte[] LAND_MASK = {
        1, 0, 1,
        0, 1, 0,
        1, 1, 0,
        0, 0, 1,
        1, 0, 1
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesIndependentRasterDimensionsAndPadsPartialEdgeTiles() throws Exception {
        Inputs inputs = createInputs("complete");
        Path output = temporaryDirectory.resolve("compiled-atlas");

        RasterAtlasCompiler.CompilationResult result = RasterAtlasCompiler.compile(
                inputs.manifest(), inputs.elevation(), inputs.landMask(), output);

        assertEquals("phase1-full-raster-test", result.atlasId());
        assertEquals(2, result.layerCount());
        assertEquals(12, result.tileCount());
        assertEquals(30, result.sourceSampleCount());
        assertEquals(output.toAbsolutePath().normalize(), result.outputDirectory());

        AtlasDirectory atlas = AtlasDirectory.open(output, 8);
        assertEquals(testManifest(), atlas.manifest());
        AtlasLayer elevationLayer = atlas.requireElevationLayer("elevation");
        AtlasLayer landMaskLayer = atlas.requireLandMaskLayer("land_mask");
        assertEquals(3, elevationLayer.tileColumns());
        assertEquals(2, elevationLayer.tileRows());
        assertEquals(2, landMaskLayer.tileColumns());
        assertEquals(3, landMaskLayer.tileRows());

        ElevationTile elevationEdge = elevationLayer.readElevationTile(2, 1);
        assertEquals(14, elevationEdge.elevationMetres(0, 0));
        assertEquals(ElevationTile.NO_DATA, elevationEdge.elevationMetres(1, 0));
        assertEquals(ElevationTile.NO_DATA, elevationEdge.elevationMetres(0, 1));
        assertEquals(ElevationTile.NO_DATA, elevationEdge.elevationMetres(1, 1));

        LandMaskTile landEdge = landMaskLayer.readLandMaskTile(1, 2);
        assertTrue(landEdge.isLand(0, 0));
        assertFalse(landEdge.isLand(1, 0));
        assertFalse(landEdge.isLand(0, 1));
        assertFalse(landEdge.isLand(1, 1));

        ElevationSampler elevation = new ElevationSampler(atlas, "elevation");
        LandMaskSampler land = new LandMaskSampler(atlas, "land_mask");
        assertEquals(OptionalInt.of(14), elevation.sampleNearestMetres(0.0, 4.0));
        assertTrue(land.isLand(0.0, 4.0));
    }

    @Test
    void compilationIsByteForByteDeterministic() throws Exception {
        Inputs inputs = createInputs("deterministic");
        Path first = temporaryDirectory.resolve("first-atlas");
        Path second = temporaryDirectory.resolve("second-atlas");

        RasterAtlasCompiler.compile(
                inputs.manifest(), inputs.elevation(), inputs.landMask(), first);
        RasterAtlasCompiler.compile(
                inputs.manifest(), inputs.elevation(), inputs.landMask(), second);

        List<String> firstFiles = relativeFiles(first);
        List<String> secondFiles = relativeFiles(second);
        assertEquals(firstFiles, secondFiles);
        for (String relative : firstFiles) {
            assertArrayEquals(
                    Files.readAllBytes(first.resolve(relative)),
                    Files.readAllBytes(second.resolve(relative)),
                    relative);
        }
    }

    @Test
    void rejectsInvalidInputsAndRemovesPartialOutput() throws Exception {
        Inputs inputs = createInputs("invalid");
        Path shortElevation = temporaryDirectory.resolve("short-elevation.raw");
        byte[] elevationBytes = Files.readAllBytes(inputs.elevation());
        Files.write(shortElevation, java.util.Arrays.copyOf(elevationBytes, elevationBytes.length - 2));
        Path sizeFailure = temporaryDirectory.resolve("size-failure");

        assertThrows(
                IOException.class,
                () -> RasterAtlasCompiler.compile(
                        inputs.manifest(), shortElevation, inputs.landMask(), sizeFailure));
        assertFalse(Files.exists(sizeFailure));

        Path invalidMask = temporaryDirectory.resolve("invalid-mask.raw");
        byte[] invalidMaskBytes = LAND_MASK.clone();
        invalidMaskBytes[7] = 2;
        Files.write(invalidMask, invalidMaskBytes);
        Path valueFailure = temporaryDirectory.resolve("value-failure");

        IOException exception = assertThrows(
                IOException.class,
                () -> RasterAtlasCompiler.compile(
                        inputs.manifest(), inputs.elevation(), invalidMask, valueFailure));
        assertTrue(exception.getMessage().contains("must be 0 or 1"));
        assertFalse(Files.exists(valueFailure));

        Path existing = temporaryDirectory.resolve("existing-output");
        Files.createDirectories(existing);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> RasterAtlasCompiler.compile(
                        inputs.manifest(), inputs.elevation(), inputs.landMask(), existing));
    }

    @Test
    void compilerCommandBuildsAnOpenableAtlas() throws Exception {
        Inputs inputs = createInputs("cli");
        Path output = temporaryDirectory.resolve("cli-atlas");

        String console = runCommand(
                "compile-raster-atlas",
                inputs.manifest().toString(),
                inputs.elevation().toString(),
                inputs.landMask().toString(),
                output.toString());

        assertTrue(console.contains("Compiled raster atlas phase1-full-raster-test with 12 tiles"));
        assertEquals(
                OptionalInt.of(14),
                new ElevationSampler(AtlasDirectory.open(output), "elevation")
                        .sampleNearestMetres(0.0, 4.0));
    }

    private Inputs createInputs(String name) throws IOException {
        Path root = temporaryDirectory.resolve(name + "-inputs");
        Files.createDirectories(root);
        Path manifest = root.resolve("manifest.json");
        Path elevation = root.resolve("elevation.raw");
        Path landMask = root.resolve("land-mask.raw");
        AtlasManifestJson.write(manifest, testManifest());
        Files.write(elevation, encodeElevation(ELEVATION));
        Files.write(landMask, LAND_MASK);
        return new Inputs(manifest, elevation, landMask);
    }

    private static byte[] encodeElevation(short[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(Math.multiplyExact(values.length, Short.BYTES))
                .order(ByteOrder.LITTLE_ENDIAN);
        for (short value : values) {
            buffer.putShort(value);
        }
        return buffer.array();
    }

    private static List<String> relativeFiles(Path root) throws IOException {
        List<String> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted(Comparator.naturalOrder())
                    .forEach(files::add);
        }
        return List.copyOf(files);
    }

    private static String runCommand(String... args) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            AtlasCompilerCli.main(args);
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static AtlasManifest testManifest() {
        return new AtlasManifest(
                AtlasManifest.CURRENT_SCHEMA_VERSION,
                "phase1-full-raster-test",
                "1.0.0",
                "0.1.0-SNAPSHOT",
                AtlasManifest.Projection.EQUIRECTANGULAR,
                new GeoBounds(0.0, 0.0, 4.0, 4.0),
                List.of(
                        new AtlasManifest.Layer(
                                "elevation",
                                AtlasManifest.LayerType.ELEVATION,
                                1,
                                AtlasManifest.Encoding.SIGNED_INT16_LE,
                                2,
                                0,
                                5,
                                3,
                                (int) ElevationTile.NO_DATA,
                                "layers/elevation/{z}/{x}/{y}.otat"),
                        new AtlasManifest.Layer(
                                "land_mask",
                                AtlasManifest.LayerType.LAND_MASK,
                                1,
                                AtlasManifest.Encoding.PACKED_BITSET_LSB0,
                                2,
                                0,
                                3,
                                5,
                                null,
                                "layers/land-mask/{z}/{x}/{y}.otat")),
                List.of(new AtlasManifest.Provenance(
                        "synthetic-full-raster-test",
                        "Synthetic full-raster compiler input",
                        "1",
                        "Orbis Terrae project test data",
                        "Generated for deterministic full-raster tiling tests",
                        "https://orbis-terrae.invalid/fixtures/full-raster",
                        "2026-07-26",
                        List.of("Created normalized row-major elevation and land-mask rasters"))));
    }

    private record Inputs(Path manifest, Path elevation, Path landMask) {
    }
}
