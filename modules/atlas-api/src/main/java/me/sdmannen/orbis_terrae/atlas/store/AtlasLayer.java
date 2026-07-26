package me.sdmannen.orbis_terrae.atlas.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import me.sdmannen.orbis_terrae.atlas.format.AtlasTile;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.format.LandMaskTile;
import me.sdmannen.orbis_terrae.atlas.manifest.AtlasManifest;

/** Runtime handle for one tile layer declared by an atlas manifest. */
public final class AtlasLayer {
    private final AtlasManifest.Layer definition;
    private final AtlasTileStore tileStore;
    private final int tileColumns;
    private final int tileRows;

    AtlasLayer(AtlasManifest.Layer definition, AtlasTileStore tileStore) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.tileStore = Objects.requireNonNull(tileStore, "tileStore");
        this.tileColumns = tileCount(definition.gridWidthSamples(), definition.tileSize());
        this.tileRows = tileCount(definition.gridHeightSamples(), definition.tileSize());
    }

    public AtlasManifest.Layer definition() {
        return definition;
    }

    public String id() {
        return definition.id();
    }

    public AtlasManifest.LayerType type() {
        return definition.type();
    }

    public int tileSize() {
        return definition.tileSize();
    }

    public int zoom() {
        return definition.zoom();
    }

    public int tileColumns() {
        return tileColumns;
    }

    public int tileRows() {
        return tileRows;
    }

    public boolean containsTile(int tileX, int tileY) {
        return tileX >= 0 && tileX < tileColumns && tileY >= 0 && tileY < tileRows;
    }

    public Path tilePath(int tileX, int tileY) throws AtlasAccessException {
        checkTileCoordinates(tileX, tileY);
        return tileStore.tilePath(definition, tileX, tileY);
    }

    public AtlasTile readTile(int tileX, int tileY) throws IOException {
        checkTileCoordinates(tileX, tileY);
        return tileStore.read(definition, tileX, tileY);
    }

    public ElevationTile readElevationTile(int tileX, int tileY) throws IOException {
        requireType(AtlasManifest.LayerType.ELEVATION);
        return (ElevationTile) readTile(tileX, tileY);
    }

    public LandMaskTile readLandMaskTile(int tileX, int tileY) throws IOException {
        requireType(AtlasManifest.LayerType.LAND_MASK);
        return (LandMaskTile) readTile(tileX, tileY);
    }

    private void requireType(AtlasManifest.LayerType required) {
        if (definition.type() != required) {
            throw new IllegalStateException(
                    "Layer " + definition.id() + " has type " + definition.type()
                            + ", not " + required);
        }
    }

    private void checkTileCoordinates(int tileX, int tileY) {
        if (!containsTile(tileX, tileY)) {
            throw new IndexOutOfBoundsException(
                    "Tile " + tileX + "," + tileY + " outside layer " + definition.id()
                            + " bounds 0.." + (tileColumns - 1) + ",0.." + (tileRows - 1));
        }
    }

    private static int tileCount(int sampleCount, int tileSize) {
        return Math.floorDiv(sampleCount - 1, tileSize) + 1;
    }
}
