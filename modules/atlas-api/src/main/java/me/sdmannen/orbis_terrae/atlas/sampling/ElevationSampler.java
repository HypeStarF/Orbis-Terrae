package me.sdmannen.orbis_terrae.atlas.sampling;

import java.io.IOException;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import me.sdmannen.orbis_terrae.atlas.format.ElevationTile;
import me.sdmannen.orbis_terrae.atlas.geo.EquirectangularGrid;
import me.sdmannen.orbis_terrae.atlas.geo.GridCoordinate;
import me.sdmannen.orbis_terrae.atlas.store.AtlasDirectory;
import me.sdmannen.orbis_terrae.atlas.store.AtlasLayer;

/** Samples one elevation layer by geographic latitude and longitude. */
public final class ElevationSampler {
    private final LayerSampleReader samples;
    private final EquirectangularGrid grid;

    public ElevationSampler(AtlasDirectory atlas, String layerId) {
        Objects.requireNonNull(atlas, "atlas");
        AtlasLayer layer = atlas.requireElevationLayer(Objects.requireNonNull(layerId, "layerId"));
        this.samples = new LayerSampleReader(layer);
        this.grid = new EquirectangularGrid(
                atlas.manifest().bounds(), samples.widthSamples(), samples.heightSamples());
    }

    /** Returns the nearest stored elevation sample, or empty when that sample is no-data. */
    public OptionalInt sampleNearestMetres(double latitude, double longitude) throws IOException {
        GridCoordinate coordinate = grid.toGrid(latitude, longitude);
        int sampleX = nearestIndex(coordinate.sampleX(), samples.widthSamples());
        int sampleY = nearestIndex(coordinate.sampleY(), samples.heightSamples());
        short elevation = samples.elevation(sampleX, sampleY);
        if (elevation == ElevationTile.NO_DATA) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(elevation);
    }

    /**
     * Bilinearly interpolates the four surrounding elevation samples.
     *
     * <p>The result is empty when any sample with a non-zero interpolation weight is no-data.
     */
    public OptionalDouble sampleBilinearMetres(double latitude, double longitude) throws IOException {
        GridCoordinate coordinate = grid.toGrid(latitude, longitude);
        int x0 = coordinate.floorX();
        int y0 = coordinate.floorY();
        int x1 = Math.min(x0 + 1, samples.widthSamples() - 1);
        int y1 = Math.min(y0 + 1, samples.heightSamples() - 1);
        double fractionX = x1 == x0 ? 0.0 : coordinate.sampleX() - x0;
        double fractionY = y1 == y0 ? 0.0 : coordinate.sampleY() - y0;

        WeightedElevation accumulator = new WeightedElevation();
        if (!accumulator.add(samples.elevation(x0, y0), (1.0 - fractionX) * (1.0 - fractionY))
                || !accumulator.add(samples.elevation(x1, y0), fractionX * (1.0 - fractionY))
                || !accumulator.add(samples.elevation(x0, y1), (1.0 - fractionX) * fractionY)
                || !accumulator.add(samples.elevation(x1, y1), fractionX * fractionY)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(accumulator.average());
    }

    private static int nearestIndex(double coordinate, int sampleCount) {
        return Math.min((int) Math.floor(coordinate + 0.5), sampleCount - 1);
    }

    private static final class WeightedElevation {
        private double weightedSum;
        private double totalWeight;

        boolean add(short elevation, double weight) {
            if (weight == 0.0) {
                return true;
            }
            if (elevation == ElevationTile.NO_DATA) {
                return false;
            }
            weightedSum += elevation * weight;
            totalWeight += weight;
            return true;
        }

        double average() {
            if (totalWeight <= 0.0) {
                throw new IllegalStateException("Bilinear interpolation produced no weighted samples");
            }
            return weightedSum / totalWeight;
        }
    }
}
