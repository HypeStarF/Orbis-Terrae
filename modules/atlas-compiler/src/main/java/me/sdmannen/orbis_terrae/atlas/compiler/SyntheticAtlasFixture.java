package me.sdmannen.orbis_terrae.atlas.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;

/** Generates and verifies the permanent Phase 1 multi-tile reference atlas. */
public final class SyntheticAtlasFixture {
    public static final String ATLAS_ID = "phase1-multi-tile-fixture";
    public static final String MANIFEST_FILE_NAME = "atlas-manifest.json";
    public static final int GRID_SIZE = 4;
    public static final int TILE_SIZE = 2;

    private static final short[] ELEVATIONS = {
        0, 10, 20, 30,
        100, 110, 120, 130,
        200, 210, 220, 230,
        300, 310, 320, ElevationTile.NO_DATA
    };
    private static final boolean[] LAND = {
        true, true, false, false,
        true, false, false, false,
        true, true, true, false,
        false, false, true, true
    };

    private SyntheticAtlasFixture() {
    }

    /** Writes the canonical fixture files below the supplied directory. */
    public static void write(Path outputDirectory) throws IOException {
        Path root = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath()
                .normalize();
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new NotDirectoryException(root.toString());
        }
        Files.createDirectories(root);
        for (Map.Entry<String, byte[]> entry : expectedFiles().entrySet()) {
            Path output = root.resolve(entry.getKey()).normalize();
            if (!output.startsWith(root)) {
                throw new IOException("Fixture path escapes output directory: " + entry.getKey());
            }
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(output, entry.getValue());
        }
    }

    /** Verifies that a directory exactly matches the canonical fixture byte-for-byte. */
    public static void verify(Path fixtureDirectory) throws IOException {
        Path root = requireDirectory(fixtureDirectory);
        Map<String, byte[]> expected = expectedFiles();
        Set<String> expectedPaths = new TreeSet<>(expected.keySet());
        Set<String> actualPaths = listRelativeFiles(root);
        if (!expectedPaths.equals(actualPaths)) {
            throw new IOException(
                    "Fixture file set differs; expected " + expectedPaths + ", got " + actualPaths);
        }
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            byte[] actual = Files.readAllBytes(root.resolve(entry.getKey()));
            if (!Arrays.equals(entry.getValue(), actual)) {
                throw new IOException("Fixture file differs from canonical bytes: " + entry.getKey());
            }
        }
    }

    /** Returns the canonical manifest used by the reference atlas. */
    public static AtlasManifest manifest() {
        return new AtlasManifest(
                AtlasManifest.CURRENT_SCHEMA_VERSION,
                ATLAS_ID,
                "1.0.0",
                "0.1.0-SNAPSHOT",
                AtlasManifest.Projection.EQUIRECTANGULAR,
                new GeoBounds(0.0, 0.0, 3.0, 3.0),
                List.of(
                        new AtlasManifest.Layer(
                                "elevation",
                                AtlasManifest.LayerType.ELEVATION,
                                1,
                                AtlasManifest.Encoding.SIGNED_INT16_LE,
                                TILE_SIZE,
                                0,
                                GRID_SIZE,
                                GRID_SIZE,
                                (int) ElevationTile.NO_DATA,
                                "layers/elevation/{z}/{x}/{y}.otat"),
                        new AtlasManifest.Layer(
                                "land_mask",
                                AtlasManifest.LayerType.LAND_MASK,
                                1,
                                AtlasManifest.Encoding.PACKED_BITSET_LSB0,
                                TILE_SIZE,
                                0,
                                GRID_SIZE,
                                GRID_SIZE,
                                null,
                                "layers/land-mask/{z}/{x}/{y}.otat")),
                List.of(new AtlasManifest.Provenance(
                        "synthetic-phase1-multi-tile",
                        "Synthetic Phase 1 multi-tile reference atlas",
                        "1",
                        "Orbis Terrae project test fixture",
                        "Generated deterministically by the Orbis Terrae atlas compiler",
                        "https://orbis-terrae.invalid/fixtures/phase1-multi-tile",
                        "2026-07-26",
                        List.of(
                                "Created a 4x4 equirectangular sample grid",
                                "Split elevation and land-mask layers into four 2x2 tiles",
                                "Inserted one explicit elevation no-data sample"))));
    }

    static Map<String, byte[]> expectedFiles() throws IOException {
        AtlasTileWriter writer = new AtlasTileWriter();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                MANIFEST_FILE_NAME,
                AtlasManifestJson.encode(manifest()).getBytes(StandardCharsets.UTF_8));
        for (int tileY = 0; tileY < 2; tileY++) {
            for (int tileX = 0; tileX < 2; tileX++) {
                files.put(
                        elevationPath(tileX, tileY),
                        writer.encodeElevation(TILE_SIZE, elevationTile(tileX, tileY)));
            }
        }
        for (int tileY = 0; tileY < 2; tileY++) {
            for (int tileX = 0; tileX < 2; tileX++) {
                files.put(
                        landMaskPath(tileX, tileY),
                        writer.encodeLandMask(TILE_SIZE, landMaskTile(tileX, tileY)));
            }
        }
        return Collections.unmodifiableMap(files);
    }

    private static short[] elevationTile(int tileX, int tileY) {
        short[] tile = new short[TILE_SIZE * TILE_SIZE];
        for (int localY = 0; localY < TILE_SIZE; localY++) {
            for (int localX = 0; localX < TILE_SIZE; localX++) {
                int globalX = tileX * TILE_SIZE + localX;
                int globalY = tileY * TILE_SIZE + localY;
                tile[localY * TILE_SIZE + localX] = ELEVATIONS[globalY * GRID_SIZE + globalX];
            }
        }
        return tile;
    }

    private static BitSet landMaskTile(int tileX, int tileY) {
        BitSet tile = new BitSet(TILE_SIZE * TILE_SIZE);
        for (int localY = 0; localY < TILE_SIZE; localY++) {
            for (int localX = 0; localX < TILE_SIZE; localX++) {
                int globalX = tileX * TILE_SIZE + localX;
                int globalY = tileY * TILE_SIZE + localY;
                if (LAND[globalY * GRID_SIZE + globalX]) {
                    tile.set(localY * TILE_SIZE + localX);
                }
            }
        }
        return tile;
    }

    private static String elevationPath(int tileX, int tileY) {
        return "layers/elevation/0/" + tileX + "/" + tileY + ".otat";
    }

    private static String landMaskPath(int tileX, int tileY) {
        return "layers/land-mask/0/" + tileX + "/" + tileY + ".otat";
    }

    private static Path requireDirectory(Path fixtureDirectory) throws IOException {
        Path root = Objects.requireNonNull(fixtureDirectory, "fixtureDirectory")
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(root)) {
            throw new NoSuchFileException(root.toString());
        }
        if (!Files.isDirectory(root)) {
            throw new NotDirectoryException(root.toString());
        }
        return root.toRealPath();
    }

    private static Set<String> listRelativeFiles(Path root) throws IOException {
        Set<String> files = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .forEach(files::add);
        }
        return files;
    }
}
