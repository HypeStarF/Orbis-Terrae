package me.sdmannen.orbis_terrae.atlas.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import me.sdmannen.orbis_terrae.atlas.format.AtlasFormatException;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.format.LandMaskTile;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtlasDirectoryTest {
    private static final short[] ELEVATION_SAMPLES = {10, 20, 30, 40};
    private final AtlasTileWriter tileWriter = new AtlasTileWriter();

    @TempDir
    Path temporaryDirectory;

    @Test
    void opensAtlasAndExposesDeclaredLayersInOrder() throws Exception {
        Path root = createAtlas("open-atlas");

        AtlasDirectory atlas = AtlasDirectory.open(root, 2);
        AtlasLayer elevation = atlas.requireElevationLayer("elevation");

        assertEquals(root.toRealPath(), atlas.rootDirectory());
        assertEquals("phase1-directory-fixture", atlas.manifest().atlasId());
        assertEquals(
                List.of("elevation", "land_mask"),
                atlas.layers().stream().map(AtlasLayer::id).toList());
        assertEquals(2, elevation.tileColumns());
        assertEquals(1, elevation.tileRows());
        assertTrue(elevation.containsTile(1, 0));
        assertFalse(elevation.containsTile(2, 0));
        assertEquals(
                root.resolve("layers/elevation/0/1/0.otat").toAbsolutePath().normalize(),
                elevation.tilePath(1, 0));
    }

    @Test
    void readsTypedTilesAndReusesSharedCacheEntries() throws Exception {
        Path root = createAtlas("cached-atlas");
        AtlasDirectory atlas = AtlasDirectory.open(root, 2);

        ElevationTile first = atlas.requireElevationLayer("elevation").readElevationTile(0, 0);
        ElevationTile second = atlas.requireElevationLayer("elevation").readElevationTile(0, 0);
        LandMaskTile landMask = atlas.requireLandMaskLayer("land_mask").readLandMaskTile(0, 0);

        assertSame(first, second);
        assertEquals(40, first.elevationMetres(1, 1));
        assertTrue(landMask.isLand(0, 0));
        assertFalse(landMask.isLand(1, 0));
        assertEquals(2, atlas.cacheStats().size());
        assertEquals(1, atlas.cacheStats().hits());
        assertEquals(2, atlas.cacheStats().misses());
    }

    @Test
    void cacheEvictionIsSharedAcrossAllLayers() throws Exception {
        Path root = createAtlas("eviction-atlas");
        AtlasDirectory atlas = AtlasDirectory.open(root, 1);
        AtlasLayer elevation = atlas.requireElevationLayer("elevation");
        AtlasLayer landMask = atlas.requireLandMaskLayer("land_mask");

        elevation.readElevationTile(0, 0);
        landMask.readLandMaskTile(0, 0);
        elevation.readElevationTile(0, 0);

        assertEquals(1, atlas.cacheStats().size());
        assertEquals(0, atlas.cacheStats().hits());
        assertEquals(3, atlas.cacheStats().misses());
        assertEquals(2, atlas.cacheStats().evictions());
    }

    @Test
    void rejectsUnknownAndIncorrectlyTypedLayers() throws Exception {
        AtlasDirectory atlas = AtlasDirectory.open(createAtlas("typed-atlas"));

        assertTrue(atlas.findLayer("missing").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> atlas.requireLayer("missing"));
        assertThrows(
                IllegalArgumentException.class,
                () -> atlas.requireElevationLayer("land_mask"));
        assertThrows(
                IllegalStateException.class,
                () -> atlas.requireLandMaskLayer("land_mask").readElevationTile(0, 0));
    }

    @Test
    void rejectsOutOfRangeTilesBeforeReadingStorage() throws Exception {
        AtlasDirectory atlas = AtlasDirectory.open(createAtlas("range-atlas"));
        AtlasLayer elevation = atlas.requireElevationLayer("elevation");

        assertThrows(IndexOutOfBoundsException.class, () -> elevation.readTile(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> elevation.tilePath(-1, 0));
        assertEquals(0, atlas.cacheStats().misses());
    }

    @Test
    void reportsMissingDeclaredTilesClearly() throws Exception {
        Path root = createAtlas("missing-tile-atlas");
        AtlasDirectory atlas = AtlasDirectory.open(root);

        NoSuchFileException exception = assertThrows(
                NoSuchFileException.class,
                () -> atlas.requireElevationLayer("elevation").readElevationTile(1, 0));

        assertTrue(Path.of(exception.getFile()).endsWith(
                Path.of("layers", "elevation", "0", "1", "0.otat")));
        assertTrue(exception.getReason().contains("Missing tile for layer elevation"));
    }

    @Test
    void rejectsTileLayerTypeThatDisagreesWithManifest() throws Exception {
        Path root = createAtlas("wrong-type-atlas");
        BitSet land = new BitSet(4);
        land.set(0);
        overwriteElevationTile(root, tileWriter.encodeLandMask(2, land));
        AtlasDirectory atlas = AtlasDirectory.open(root);

        AtlasAccessException exception = assertThrows(
                AtlasAccessException.class,
                () -> atlas.requireElevationLayer("elevation").readElevationTile(0, 0));

        assertTrue(exception.getMessage().contains("layer type"));
        assertTrue(exception.getMessage().contains("elevation"));
    }

    @Test
    void rejectsTileSizeThatDisagreesWithManifest() throws Exception {
        Path root = createAtlas("wrong-size-atlas");
        overwriteElevationTile(
                root,
                tileWriter.encodeElevation(3, new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9}));
        AtlasDirectory atlas = AtlasDirectory.open(root);

        AtlasAccessException exception = assertThrows(
                AtlasAccessException.class,
                () -> atlas.requireElevationLayer("elevation").readElevationTile(0, 0));

        assertTrue(exception.getMessage().contains("tile size"));
        assertTrue(exception.getMessage().contains("expected 2"));
    }

    @Test
    void propagatesCorruptTileDetection() throws Exception {
        Path root = createAtlas("corrupt-atlas");
        Path tilePath = elevationTilePath(root);
        byte[] bytes = Files.readAllBytes(tilePath);
        bytes[bytes.length - 1] ^= 1;
        Files.write(tilePath, bytes);
        AtlasDirectory atlas = AtlasDirectory.open(root);

        AtlasFormatException exception = assertThrows(
                AtlasFormatException.class,
                () -> atlas.requireElevationLayer("elevation").readElevationTile(0, 0));

        assertTrue(exception.getMessage().contains("CRC32"));
    }

    @Test
    void rejectsTileSymbolicLinkOutsideAtlasRoot() throws Exception {
        Path root = createAtlas("tile-link-atlas");
        Path externalTile = temporaryDirectory.resolve("external-tile.otat");
        Files.write(externalTile, tileWriter.encodeElevation(2, ELEVATION_SAMPLES));
        createSymbolicLinkOrSkip(elevationTilePath(root), externalTile.toAbsolutePath());
        AtlasDirectory atlas = AtlasDirectory.open(root);

        AtlasAccessException exception = assertThrows(
                AtlasAccessException.class,
                () -> atlas.requireElevationLayer("elevation").readElevationTile(0, 0));

        assertTrue(exception.getMessage().contains("outside the atlas directory"));
    }

    @Test
    void rejectsManifestSymbolicLinkOutsideAtlasRoot() throws Exception {
        Path root = temporaryDirectory.resolve("manifest-link-atlas");
        Files.createDirectories(root);
        Path externalManifest = temporaryDirectory.resolve("external-manifest.json");
        AtlasManifestJson.write(externalManifest, manifest());
        createSymbolicLinkOrSkip(
                root.resolve(AtlasDirectory.MANIFEST_FILE_NAME),
                externalManifest.toAbsolutePath());

        AtlasAccessException exception = assertThrows(
                AtlasAccessException.class,
                () -> AtlasDirectory.open(root));

        assertTrue(exception.getMessage().contains("manifest resolves outside"));
    }

    @Test
    void rejectsMissingDirectoryNonDirectoryAndMissingManifest() throws Exception {
        Path missing = temporaryDirectory.resolve("missing");
        Path file = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(file, "not a directory\n");
        Path emptyDirectory = temporaryDirectory.resolve("empty");
        Files.createDirectories(emptyDirectory);

        assertThrows(NoSuchFileException.class, () -> AtlasDirectory.open(missing));
        assertThrows(NotDirectoryException.class, () -> AtlasDirectory.open(file));
        assertThrows(NoSuchFileException.class, () -> AtlasDirectory.open(emptyDirectory));
        assertThrows(IllegalArgumentException.class, () -> AtlasDirectory.open(emptyDirectory, 0));
    }

    @Test
    void clearCacheRemovesLoadedTiles() throws Exception {
        AtlasDirectory atlas = AtlasDirectory.open(createAtlas("clear-cache-atlas"));
        atlas.requireElevationLayer("elevation").readElevationTile(0, 0);

        atlas.clearCache();

        assertEquals(0, atlas.cacheStats().size());
        assertEquals(1, atlas.cacheStats().misses());
    }

    private Path createAtlas(String directoryName) throws IOException {
        Path root = temporaryDirectory.resolve(directoryName);
        Files.createDirectories(root);
        AtlasManifestJson.write(root.resolve(AtlasDirectory.MANIFEST_FILE_NAME), manifest());
        writeTile(elevationTilePath(root), tileWriter.encodeElevation(2, ELEVATION_SAMPLES));

        BitSet land = new BitSet(4);
        land.set(0);
        land.set(3);
        writeTile(
                root.resolve("layers/land-mask/0/0/0.otat"),
                tileWriter.encodeLandMask(2, land));
        return root;
    }

    private void overwriteElevationTile(Path root, byte[] bytes) throws IOException {
        Files.write(elevationTilePath(root), bytes);
    }

    private static Path elevationTilePath(Path root) {
        return root.resolve("layers/elevation/0/0/0.otat");
    }

    private static void writeTile(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        Files.deleteIfExists(link);
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "Symbolic links unavailable: " + exception.getMessage());
        }
    }

    private static AtlasManifest manifest() {
        return new AtlasManifest(
                AtlasManifest.CURRENT_SCHEMA_VERSION,
                "phase1-directory-fixture",
                "1.0.0",
                "0.1.0-SNAPSHOT",
                AtlasManifest.Projection.EQUIRECTANGULAR,
                new GeoBounds(-25.0, 54.0, 45.0, 72.0),
                List.of(
                        new AtlasManifest.Layer(
                                "elevation",
                                AtlasManifest.LayerType.ELEVATION,
                                1,
                                AtlasManifest.Encoding.SIGNED_INT16_LE,
                                2,
                                0,
                                3,
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
                                3,
                                2,
                                null,
                                "layers/land-mask/{z}/{x}/{y}.otat")),
                List.of(new AtlasManifest.Provenance(
                        "synthetic-directory-fixture",
                        "Synthetic atlas directory fixture",
                        "1",
                        "Orbis Terrae project test fixture",
                        "Generated for atlas directory tests",
                        "https://orbis-terrae.invalid/fixtures/atlas-directory",
                        "2026-07-26",
                        List.of("Generated deterministic manifest and OTAT tiles"))));
    }
}
