package me.sdmannen.orbis_terrae.atlas.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

final class AtlasStackTest {
    private static final int GRID_SIZE = 4;
    private static final int TILE_SIZE = 2;
    private static final GeoBounds GLOBAL_BOUNDS = new GeoBounds(-135.0, -67.5, 135.0, 67.5);
    private static final GeoBounds REGIONAL_BOUNDS = new GeoBounds(0.0, 0.0, 3.0, 3.0);

    private final AtlasTileWriter tileWriter = new AtlasTileWriter();

    @TempDir
    Path temporaryDirectory;

    @Test
    void selectsHighestResolutionIndependentlyForEachLayer() throws Exception {
        AtlasDirectory global = AtlasDirectory.open(createAtlas(
                "global",
                "global-atlas",
                GLOBAL_BOUNDS,
                gradientElevations(),
                constantLand(false),
                LayerSet.BOTH));
        AtlasDirectory regionalElevation = AtlasDirectory.open(createAtlas(
                "regional-elevation",
                "regional-elevation-atlas",
                REGIONAL_BOUNDS,
                constantElevations(500),
                constantLand(true),
                LayerSet.ELEVATION_ONLY));
        AtlasStack stack = AtlasStack.of(global, regionalElevation);

        AtlasStack.ElevationSample regional = stack
                .sampleNearestElevationMetres(1.5, 1.5)
                .orElseThrow();
        AtlasStack.LandMaskSample globalLand = stack.sampleLandMask(1.5, 1.5).orElseThrow();
        AtlasStack.ElevationSample outside = stack
                .sampleNearestElevationMetres(20.0, 20.0)
                .orElseThrow();

        assertEquals(List.of("global-atlas", "regional-elevation-atlas"), stack.atlasIds());
        assertEquals(500.0, regional.metres());
        assertEquals("regional-elevation-atlas", regional.atlasId());
        assertFalse(globalLand.land());
        assertEquals("global-atlas", globalLand.atlasId());
        assertEquals("global-atlas", outside.atlasId());
    }

    @Test
    void fallsBackForNoDataMissingTilesAndCorruptTiles() throws Exception {
        AtlasDirectory global = AtlasDirectory.open(createAtlas(
                "fallback-global",
                "fallback-global-atlas",
                GLOBAL_BOUNDS,
                gradientElevations(),
                constantLand(false),
                LayerSet.BOTH));

        short[] noDataValues = constantElevations(500);
        noDataValues[1 * GRID_SIZE + 1] = ElevationTile.NO_DATA;
        AtlasDirectory noDataRegional = AtlasDirectory.open(createAtlas(
                "no-data-regional",
                "no-data-regional-atlas",
                REGIONAL_BOUNDS,
                noDataValues,
                constantLand(true),
                LayerSet.BOTH));
        AtlasStack noDataStack = AtlasStack.of(global, noDataRegional);

        assertEquals(
                "fallback-global-atlas",
                noDataStack.sampleNearestElevationMetres(2.0, 1.0).orElseThrow().atlasId());
        assertEquals(
                "fallback-global-atlas",
                noDataStack.sampleBilinearElevationMetres(2.0, 1.0).orElseThrow().atlasId());

        Path missingRoot = createAtlas(
                "missing-regional",
                "missing-regional-atlas",
                REGIONAL_BOUNDS,
                constantElevations(600),
                constantLand(true),
                LayerSet.BOTH);
        Files.delete(tilePath(missingRoot, "land-mask", 0, 0));
        AtlasStack missingStack = AtlasStack.of(global, AtlasDirectory.open(missingRoot));
        AtlasStack.LandMaskSample missingFallback = missingStack
                .sampleLandMask(2.5, 0.5)
                .orElseThrow();
        assertFalse(missingFallback.land());
        assertEquals("fallback-global-atlas", missingFallback.atlasId());

        Path corruptRoot = createAtlas(
                "corrupt-regional",
                "corrupt-regional-atlas",
                REGIONAL_BOUNDS,
                constantElevations(700),
                constantLand(true),
                LayerSet.BOTH);
        Files.write(tilePath(corruptRoot, "elevation", 0, 0), new byte[] {1, 2, 3, 4});
        AtlasStack corruptStack = AtlasStack.of(global, AtlasDirectory.open(corruptRoot));
        assertEquals(
                "fallback-global-atlas",
                corruptStack.sampleNearestElevationMetres(2.5, 0.5).orElseThrow().atlasId());
    }

    @Test
    void normalizesAntimeridianLongitudesAndClampsPolarLatitudes() throws Exception {
        AtlasStack stack = AtlasStack.of(AtlasDirectory.open(createAtlas(
                "wrapped-global",
                "wrapped-global-atlas",
                GLOBAL_BOUNDS,
                gradientElevations(),
                constantLand(false),
                LayerSet.BOTH)));

        assertEquals(10.0, stack.sampleNearestElevationMetres(67.5, -180.0).orElseThrow().metres());
        assertEquals(10.0, stack.sampleNearestElevationMetres(67.5, 180.0).orElseThrow().metres());
        assertEquals(10.0, stack.sampleNearestElevationMetres(67.5, 540.0).orElseThrow().metres());
        assertEquals(40.0, stack.sampleNearestElevationMetres(67.5, 179.9).orElseThrow().metres());
        assertEquals(10.0, stack.sampleNearestElevationMetres(120.0, -135.0).orElseThrow().metres());
        assertEquals(310.0, stack.sampleNearestElevationMetres(-120.0, -135.0).orElseThrow().metres());
    }

    @Test
    void usesHalfCellCoverageAndConstructorOrderForResolutionTies() throws Exception {
        AtlasDirectory global = AtlasDirectory.open(createAtlas(
                "edge-global",
                "edge-global-atlas",
                GLOBAL_BOUNDS,
                constantElevations(100),
                constantLand(false),
                LayerSet.BOTH));
        AtlasDirectory firstRegional = AtlasDirectory.open(createAtlas(
                "first-regional",
                "first-regional-atlas",
                REGIONAL_BOUNDS,
                constantElevations(600),
                constantLand(true),
                LayerSet.BOTH));
        AtlasDirectory secondRegional = AtlasDirectory.open(createAtlas(
                "second-regional",
                "second-regional-atlas",
                REGIONAL_BOUNDS,
                constantElevations(700),
                constantLand(false),
                LayerSet.BOTH));
        AtlasStack stack = AtlasStack.of(global, firstRegional, secondRegional);

        assertEquals(
                "first-regional-atlas",
                stack.sampleNearestElevationMetres(1.5, -0.4).orElseThrow().atlasId());
        assertEquals(
                "edge-global-atlas",
                stack.sampleNearestElevationMetres(1.5, -0.6).orElseThrow().atlasId());
        assertEquals(
                "first-regional-atlas",
                stack.sampleNearestElevationMetres(3.4, 1.5).orElseThrow().atlasId());
        assertEquals(
                "edge-global-atlas",
                stack.sampleNearestElevationMetres(3.6, 1.5).orElseThrow().atlasId());
        assertTrue(stack.sampleLandMask(1.5, 1.5).orElseThrow().land());
    }

    @Test
    void combinesFailuresWhenEveryCoveringAtlasIsUnreadable() throws Exception {
        Path globalRoot = createAtlas(
                "broken-global",
                "broken-global-atlas",
                GLOBAL_BOUNDS,
                gradientElevations(),
                constantLand(false),
                LayerSet.BOTH);
        Path regionalRoot = createAtlas(
                "broken-regional",
                "broken-regional-atlas",
                REGIONAL_BOUNDS,
                constantElevations(500),
                constantLand(true),
                LayerSet.BOTH);
        Files.delete(tilePath(globalRoot, "elevation", 1, 0));
        Files.delete(tilePath(regionalRoot, "elevation", 1, 1));
        AtlasStack stack = AtlasStack.of(
                AtlasDirectory.open(globalRoot), AtlasDirectory.open(regionalRoot));

        IOException exception = assertThrows(
                IOException.class,
                () -> stack.sampleNearestElevationMetres(1.5, 1.5));

        assertTrue(exception.getMessage().contains("No readable atlas"));
        assertTrue(exception.getCause().getMessage().contains("broken-regional-atlas"));
        assertEquals(1, exception.getSuppressed().length);
        assertTrue(exception.getSuppressed()[0].getMessage().contains("broken-global-atlas"));
    }

    @Test
    void validatesStackAndCoordinateInputs() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new AtlasStack(List.of()));

        AtlasDirectory first = AtlasDirectory.open(createAtlas(
                "duplicate-first",
                "duplicate-atlas",
                GLOBAL_BOUNDS,
                gradientElevations(),
                constantLand(false),
                LayerSet.BOTH));
        AtlasDirectory second = AtlasDirectory.open(createAtlas(
                "duplicate-second",
                "duplicate-atlas",
                REGIONAL_BOUNDS,
                constantElevations(500),
                constantLand(true),
                LayerSet.BOTH));
        assertThrows(IllegalArgumentException.class, () -> AtlasStack.of(first, second));

        AtlasStack stack = AtlasStack.of(first);
        assertThrows(
                IllegalArgumentException.class,
                () -> stack.sampleNearestElevationMetres(Double.NaN, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> stack.sampleLandMask(0.0, Double.POSITIVE_INFINITY));
    }

    private Path createAtlas(
            String directoryName,
            String atlasId,
            GeoBounds bounds,
            short[] elevations,
            boolean[] landValues,
            LayerSet layerSet) throws IOException {
        Path root = temporaryDirectory.resolve(directoryName);
        Files.createDirectories(root);
        AtlasManifestJson.write(
                root.resolve(AtlasDirectory.MANIFEST_FILE_NAME), manifest(atlasId, bounds, layerSet));
        if (layerSet.elevation()) {
            writeElevationTiles(root, elevations);
        }
        if (layerSet.landMask()) {
            writeLandMaskTiles(root, landValues);
        }
        return root;
    }

    private void writeElevationTiles(Path root, short[] elevations) throws IOException {
        for (int tileY = 0; tileY < 2; tileY++) {
            for (int tileX = 0; tileX < 2; tileX++) {
                short[] tile = new short[TILE_SIZE * TILE_SIZE];
                for (int localY = 0; localY < TILE_SIZE; localY++) {
                    for (int localX = 0; localX < TILE_SIZE; localX++) {
                        int sampleX = tileX * TILE_SIZE + localX;
                        int sampleY = tileY * TILE_SIZE + localY;
                        tile[localY * TILE_SIZE + localX] =
                                elevations[sampleY * GRID_SIZE + sampleX];
                    }
                }
                writeTile(
                        tilePath(root, "elevation", tileX, tileY),
                        tileWriter.encodeElevation(TILE_SIZE, tile));
            }
        }
    }

    private void writeLandMaskTiles(Path root, boolean[] landValues) throws IOException {
        for (int tileY = 0; tileY < 2; tileY++) {
            for (int tileX = 0; tileX < 2; tileX++) {
                BitSet land = new BitSet(TILE_SIZE * TILE_SIZE);
                for (int localY = 0; localY < TILE_SIZE; localY++) {
                    for (int localX = 0; localX < TILE_SIZE; localX++) {
                        int sampleX = tileX * TILE_SIZE + localX;
                        int sampleY = tileY * TILE_SIZE + localY;
                        if (landValues[sampleY * GRID_SIZE + sampleX]) {
                            land.set(localY * TILE_SIZE + localX);
                        }
                    }
                }
                writeTile(
                        tilePath(root, "land-mask", tileX, tileY),
                        tileWriter.encodeLandMask(TILE_SIZE, land));
            }
        }
    }

    private static Path tilePath(Path root, String layerDirectory, int tileX, int tileY) {
        return root.resolve(
                "layers/" + layerDirectory + "/0/" + tileX + "/" + tileY + ".otat");
    }

    private static void writeTile(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static short[] gradientElevations() {
        return new short[] {
            10, 20, 30, 40,
            110, 120, 130, 140,
            210, 220, 230, 240,
            310, 320, 330, 340
        };
    }

    private static short[] constantElevations(int value) {
        short[] result = new short[GRID_SIZE * GRID_SIZE];
        java.util.Arrays.fill(result, (short) value);
        return result;
    }

    private static boolean[] constantLand(boolean value) {
        boolean[] result = new boolean[GRID_SIZE * GRID_SIZE];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private static AtlasManifest manifest(String atlasId, GeoBounds bounds, LayerSet layerSet) {
        List<AtlasManifest.Layer> layers = new ArrayList<>();
        if (layerSet.elevation()) {
            layers.add(new AtlasManifest.Layer(
                    "elevation",
                    AtlasManifest.LayerType.ELEVATION,
                    1,
                    AtlasManifest.Encoding.SIGNED_INT16_LE,
                    TILE_SIZE,
                    0,
                    GRID_SIZE,
                    GRID_SIZE,
                    (int) ElevationTile.NO_DATA,
                    "layers/elevation/{z}/{x}/{y}.otat"));
        }
        if (layerSet.landMask()) {
            layers.add(new AtlasManifest.Layer(
                    "land_mask",
                    AtlasManifest.LayerType.LAND_MASK,
                    1,
                    AtlasManifest.Encoding.PACKED_BITSET_LSB0,
                    TILE_SIZE,
                    0,
                    GRID_SIZE,
                    GRID_SIZE,
                    null,
                    "layers/land-mask/{z}/{x}/{y}.otat"));
        }
        return new AtlasManifest(
                AtlasManifest.CURRENT_SCHEMA_VERSION,
                atlasId,
                "1.0.0",
                "0.1.0-SNAPSHOT",
                AtlasManifest.Projection.EQUIRECTANGULAR,
                bounds,
                layers,
                List.of(new AtlasManifest.Provenance(
                        atlasId + "-source",
                        "Synthetic atlas selection fixture",
                        "1",
                        "Orbis Terrae project test fixture",
                        "Generated for atlas selection tests",
                        "https://orbis-terrae.invalid/fixtures/atlas-selection",
                        "2026-07-26",
                        List.of("Generated deterministic selection-test tiles"))));
    }

    private enum LayerSet {
        ELEVATION_ONLY(true, false),
        BOTH(true, true);

        private final boolean elevation;
        private final boolean landMask;

        LayerSet(boolean elevation, boolean landMask) {
            this.elevation = elevation;
            this.landMask = landMask;
        }

        boolean elevation() {
            return elevation;
        }

        boolean landMask() {
            return landMask;
        }
    }
}
