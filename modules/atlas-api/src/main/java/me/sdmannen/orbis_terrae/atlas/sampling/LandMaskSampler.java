package me.sdmannen.orbis_terrae.atlas.sampling;

import java.io.IOException;
import java.util.Objects;
import me.sdmannen.orbis_terrae.atlas.geo.EquirectangularGrid;
import me.sdmannen.orbis_terrae.atlas.geo.GridCoordinate;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;

/** Samples one land-mask layer by geographic latitude and longitude. */
public final class LandMaskSampler {
    private final LayerSampleReader samples;
    private final EquirectangularGrid grid;

    public LandMaskSampler(AtlasDirectory atlas, String layerId) {
        Objects.requireNonNull(atlas, "atlas");
        AtlasLayer layer = atlas.requireLandMaskLayer(Objects.requireNonNull(layerId, "layerId"));
        this.samples = new LayerSampleReader(layer);
        this.grid = new EquirectangularGrid(
                atlas.manifest().bounds(), samples.widthSamples(), samples.heightSamples());
    }

    /** Returns the nearest stored land/water classification. */
    public boolean isLand(double latitude, double longitude) throws IOException {
        GridCoordinate coordinate = grid.toGrid(latitude, longitude);
        int sampleX = nearestIndex(coordinate.sampleX(), samples.widthSamples());
        int sampleY = nearestIndex(coordinate.sampleY(), samples.heightSamples());
        return samples.isLand(sampleX, sampleY);
    }

    private static int nearestIndex(double coordinate, int sampleCount) {
        return Math.min((int) Math.floor(coordinate + 0.5), sampleCount - 1);
    }
}
