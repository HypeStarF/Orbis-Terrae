package me.sdmannen.orbis_terrae.atlas.sampling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTileWriter;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.geo.GeoBounds;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifestJson;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GeographicSamplingTest {
    private static final int GRID_SIZE = 4;
    private static final int TILE_SIZE = 2;
    private static final short[] ELEVATIONS = {
        0, 10, 20, 30,
        100, 110, 120, 130,
        200, 210, 220, 230,
        300, 310, 320, 330
    };

    private final AtlasTileWriter tileWriter = new AtlasTileWriter();

    @TempDir
    Path temporaryDirectory;

    @Test
    void samplesNearestElevationAtExactAndFractionalCoordinates() throws Exception {
        AtlasDirectory atlas = AtlasDirectory.open(createAtlas("nearest-atlas"));
        ElevationSampler sampler = new ElevationSampler(atlas, "elevation");

        assertEquals(OptionalInt.of(0), sampler.sampleNearestMetres(3.0, 0.0));
        assertEquals(OptionalInt.of(330), sampler.sampleNearestMetres(0.0, 3.0));
        assertEquals(OptionalInt.of(20), sampler.sampleNearestMetres(2.6, 1.6));
    }

    @Test
    void bilinearInterpolationCrossesFourTileBoundaries() throws Exception {
        AtlasDirectory atlas = AtlasDirectory.open(createAtlas("four-tile-atlas"), 8);
        ElevationSampler sampler = new ElevationSampler(atlas, "elevation");

        OptionalDouble first = sampler.sampleBilinearMetres(1.5, 1.5);
        OptionalDouble second = sampler.sampleBilinearMetres(1.5, 1.5);

        assertEquals(165.0, first.orElseThrow());
        assertEquals(first, second);
        assertEquals(4, atlas.cacheStats().misses());
        assertEquals(4, atlas.cacheStats().hits());
        assertEquals(4, atlas.cacheStats().size());
    }

    @Test
    void bilinearInterpolationWorksInsideOneTileAndAtAtlasEdges() throws Exception {
        ElevationSampler sampler = new ElevationSampler(
                AtlasDirectory.open(createAtlas("edge-atlas")), "elevation");

        assertEquals(55.0, sampler.sampleBilinearMetres(2.5, 0.5).orElseThrow());
        assertEquals(330.0, sampler.sampleBilinearMetres(0.0, 3.0).orElseThrow());
    }

    @Test
    void noDataAffectsOnlySamplesWithNonZeroWeight() throws Exception {
        Path root = createAtlas("no-data-atlas");
        short[] tile = elevationTile(1, 0);
        tile[2] = ElevationTile.NO_DATA;
        writeElevationTile(root, 1, 0, tile);
        ElevationSampler sampler = new ElevationSampler(AtlasDirectory.open(root), "elevation");

        assertEquals(OptionalInt.empty(), sampler.sampleNearestMetres(2.0, 2.0));
        assertEquals(110.0, sampler.sampleBilinearMetres(2.0, 1.0).orElseThrow());
        assertEquals(OptionalDouble.empty(), sampler.sampleBilinearMetres(1.5, 1.5));
    }

    @Test
    void landMaskUsesNearestNeighbourAcrossTileBoundaries() throws Exception {
        LandMaskSampler sampler = new LandMaskSampler(
                AtlasDirectory.open(createAtlas("land-atlas")), "land_mask");

        assertTrue(sampler.isLand(3.0, 0.0));
        assertFalse(sampler.isLand(3.0, 1.0));
        assertTrue(sampler.isLand(0.4, 2.6));
    }

    @Test
    void rejectsCoordinatesOutsideAtlasBeforeReadingTiles() throws Exception {
        AtlasDirectory atlas = AtlasDirectory.open(createAtlas("bounds-atlas"));
        ElevationSampler elevation = new ElevationSampler(atlas, "elevation");
        LandMaskSampler landMask = new LandMaskSampler(atlas, "land_mask");

        assertThrows(
                IllegalArgumentException.class,
                () -> elevation.sampleBilinearMetres(3.1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> landMask.isLand(1.0, -0.1));
        assertEquals(0, atlas.cacheStats().misses());
    }

    @Test
    void rejectsSamplerLayerTypeMismatches() throws Exception {
        AtlasDirectory atlas = AtlasDirectory.open(createAtlas("typed-atlas"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ElevationSampler(atlas, "land_mask"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LandMaskSampler(atlas, "elevation"));
    }

    private Path createAtlas(String directoryName) throws IOException {
        Path root = temporaryDirectory.resolve(directoryName);
        Files.createDirectories(root);
        AtlasManifestJson.write(root.resolve(AtlasDirectory.MANIFEST_FILE_NAME), manifest());
        for (int tileY = 0; tileY < 2; tileY++) {
            for (int tileX = 0; tileX < 2; tileX++) {
                writeElevationTile(root, tileX, tileY, elevationTile(tileX, tileY));
                writeLandMaskTile(root, tileX, tileY);
            }
        }
        return root;
    }

    private void writeElevationTile(Path root, int tileX, int tileY, short[] samples)
            throws IOException {
        Path path = root.resolve("layers/elevation/0/" + tileX + "/" + tileY + ".otat");
        writeTile(path, tileWriter.encodeElevation(TILE_SIZE, samples));
    }

    private void writeLandMaskTile(Path root, int tileX, int tileY) throws IOException {
        BitSet land = new BitSet(TILE_SIZE * TILE_SIZE);
        for (int localY = 0; localY < TILE_SIZE; localY++) {
            for (int localX = 0; localX < TILE_SIZE; localX++) {
                int sampleX = tileX * TILE_SIZE + localX;
                int sampleY = tileY * TILE_SIZE + localY;
                if ((sampleX + sampleY) % 2 == 0) {
                    land.set(localY * TILE_SIZE + localX);
                }
            }
        }
        Path path = root.resolve("layers/land-mask/0/" + tileX + "/" + tileY + ".otat");
        writeTile(path, tileWriter.encodeLandMask(TILE_SIZE, land));
    }

    private static short[] elevationTile(int tileX, int tileY) {
        short[] tile = new short[TILE_SIZE * TILE_SIZE];
        for (int localY = 0; localY < TILE_SIZE; localY++) {
            for (int localX = 0; localX < TILE_SIZE; localX++) {
                int sampleX = tileX * TILE_SIZE + localX;
                int sampleY = tileY * TILE_SIZE + localY;
                tile[localY * TILE_SIZE + localX] =
                        ELEVATIONS[sampleY * GRID_SIZE + sampleX];
            }
        }
        return tile;
    }

    private static void writeTile(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static AtlasManifest manifest() {
        return new AtlasManifest(
                AtlasManifest.CURRENT_SCHEMA_VERSION,
                "phase1-sampling-fixture",
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
                        "synthetic-sampling-fixture",
                        "Synthetic geographic sampling fixture",
                        "1",
                        "Orbis Terrae project test fixture",
                        "Generated for geographic sampling tests",
                        "https://orbis-terrae.invalid/fixtures/geographic-sampling",
                        "2026-07-26",
                        List.of("Generated a deterministic four-tile raster"))));
    }
}
