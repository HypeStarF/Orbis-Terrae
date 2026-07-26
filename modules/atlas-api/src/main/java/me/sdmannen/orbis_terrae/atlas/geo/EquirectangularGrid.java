package me.sdmannen.orbis_terrae.atlas.geo;

import java.util.Objects;

public final class EquirectangularGrid {
    private final GeoBounds bounds;
    private final int widthSamples;
    private final int heightSamples;

    public EquirectangularGrid(GeoBounds bounds, int widthSamples, int heightSamples) {
        if (widthSamples < 2 || heightSamples < 2) {
            throw new IllegalArgumentException("Grid requires at least 2x2 samples");
        }
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.widthSamples = widthSamples;
        this.heightSamples = heightSamples;
    }

    public GridCoordinate toGrid(double latitude, double longitude) {
        if (!bounds.contains(latitude, longitude)) {
            throw new IllegalArgumentException("Coordinate outside atlas bounds");
        }
        double xFraction = (longitude - bounds.west()) / (bounds.east() - bounds.west());
        double yFraction = (bounds.north() - latitude) / (bounds.north() - bounds.south());
        return new GridCoordinate(
                xFraction * (widthSamples - 1),
                yFraction * (heightSamples - 1));
    }

    public GeographicCoordinate toGeographic(double sampleX, double sampleY) {
        if (!Double.isFinite(sampleX) || !Double.isFinite(sampleY)
                || sampleX < 0 || sampleY < 0
                || sampleX > widthSamples - 1 || sampleY > heightSamples - 1) {
            throw new IllegalArgumentException("Sample outside grid");
        }
        double longitude = bounds.west()
                + (sampleX / (widthSamples - 1)) * (bounds.east() - bounds.west());
        double latitude = bounds.north()
                - (sampleY / (heightSamples - 1)) * (bounds.north() - bounds.south());
        return new GeographicCoordinate(latitude, longitude);
    }

    public record GeographicCoordinate(double latitude, double longitude) {
    }
}
