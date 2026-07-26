package me.sdmannen.orbis_terrae.atlas.sampling;

import java.io.IOException;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.format.LandMaskTile;
import me.sdmannen.orbis_terrae.atlas.geo.TileAddress;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;

/** Reads global raster samples from the correct cached atlas tile. */
final class LayerSampleReader {
    private final AtlasLayer layer;
    private final int widthSamples;
    private final int heightSamples;

    LayerSampleReader(AtlasLayer layer) {
        this.layer = layer;
        this.widthSamples = layer.definition().gridWidthSamples();
        this.heightSamples = layer.definition().gridHeightSamples();
    }

    short elevation(int sampleX, int sampleY) throws IOException {
        checkSampleCoordinates(sampleX, sampleY);
        TileAddress address = TileAddress.fromSample(sampleX, sampleY, layer.tileSize());
        ElevationTile tile = layer.readElevationTile(address.tileX(), address.tileY());
        return tile.elevationMetres(address.localX(), address.localY());
    }

    boolean isLand(int sampleX, int sampleY) throws IOException {
        checkSampleCoordinates(sampleX, sampleY);
        TileAddress address = TileAddress.fromSample(sampleX, sampleY, layer.tileSize());
        LandMaskTile tile = layer.readLandMaskTile(address.tileX(), address.tileY());
        return tile.isLand(address.localX(), address.localY());
    }

    int widthSamples() {
        return widthSamples;
    }

    int heightSamples() {
        return heightSamples;
    }

    private void checkSampleCoordinates(int sampleX, int sampleY) {
        if (sampleX < 0 || sampleX >= widthSamples || sampleY < 0 || sampleY >= heightSamples) {
            throw new IndexOutOfBoundsException(
                    "Sample " + sampleX + "," + sampleY + " outside layer " + layer.id()
                            + " bounds 0.." + (widthSamples - 1) + ",0.." + (heightSamples - 1));
        }
    }
}
