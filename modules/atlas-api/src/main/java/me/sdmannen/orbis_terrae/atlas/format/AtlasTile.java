package me.sdmannen.orbis_terrae.atlas.format;

public sealed interface AtlasTile permits ElevationTile, LandMaskTile {
    AtlasTileHeader header();

    default int tileSize() {
        return header().tileSize();
    }
}
