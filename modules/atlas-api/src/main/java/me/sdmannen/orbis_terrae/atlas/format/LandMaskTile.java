package me.sdmannen.orbis_terrae.atlas.format;

import java.util.BitSet;
import java.util.Objects;

public final class LandMaskTile implements AtlasTile {
    private final AtlasTileHeader header;
    private final BitSet landSamples;

    public LandMaskTile(AtlasTileHeader header, BitSet landSamples) {
        this.header = Objects.requireNonNull(header, "header");
        this.landSamples = (BitSet) Objects.requireNonNull(landSamples, "landSamples").clone();
    }

    @Override
    public AtlasTileHeader header() {
        return header;
    }

    public boolean isLand(int x, int y) {
        checkCoordinates(x, y);
        return landSamples.get(y * tileSize() + x);
    }

    private void checkCoordinates(int x, int y) {
        if (x < 0 || y < 0 || x >= tileSize() || y >= tileSize()) {
            throw new IndexOutOfBoundsException("Sample outside tile: " + x + "," + y);
        }
    }
}
