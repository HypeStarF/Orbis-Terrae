package me.sdmannen.orbis_terrae.atlas.format;

import java.util.Arrays;
import java.util.Objects;

public final class ElevationTile implements AtlasTile {
    public static final short NO_DATA = Short.MIN_VALUE;

    private final AtlasTileHeader header;
    private final short[] elevations;

    public ElevationTile(AtlasTileHeader header, short[] elevations) {
        this.header = Objects.requireNonNull(header, "header");
        Objects.requireNonNull(elevations, "elevations");
        int expected = Math.multiplyExact(header.tileSize(), header.tileSize());
        if (elevations.length != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " samples, got " + elevations.length);
        }
        this.elevations = elevations.clone();
    }

    @Override
    public AtlasTileHeader header() {
        return header;
    }

    public short elevationMetres(int x, int y) {
        checkCoordinates(x, y);
        return elevations[y * tileSize() + x];
    }

    public boolean hasData(int x, int y) {
        return elevationMetres(x, y) != NO_DATA;
    }

    public short[] copySamples() {
        return Arrays.copyOf(elevations, elevations.length);
    }

    private void checkCoordinates(int x, int y) {
        if (x < 0 || y < 0 || x >= tileSize() || y >= tileSize()) {
            throw new IndexOutOfBoundsException("Sample outside tile: " + x + "," + y);
        }
    }
}
