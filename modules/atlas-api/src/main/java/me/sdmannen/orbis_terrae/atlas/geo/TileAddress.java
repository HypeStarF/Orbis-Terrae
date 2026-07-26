package me.sdmannen.orbis_terrae.atlas.geo;

public record TileAddress(int tileX, int tileY, int localX, int localY) {
    public static TileAddress fromSample(int sampleX, int sampleY, int tileSize) {
        if (sampleX < 0 || sampleY < 0 || tileSize < 1) {
            throw new IllegalArgumentException("Invalid sample or tile size");
        }
        return new TileAddress(
                sampleX / tileSize,
                sampleY / tileSize,
                sampleX % tileSize,
                sampleY % tileSize);
    }
}
