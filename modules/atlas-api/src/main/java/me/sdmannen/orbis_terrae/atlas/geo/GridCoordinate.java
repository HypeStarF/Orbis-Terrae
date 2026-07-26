package me.sdmannen.orbis_terrae.atlas.geo;

public record GridCoordinate(double sampleX, double sampleY) {
    public GridCoordinate {
        if (!Double.isFinite(sampleX) || !Double.isFinite(sampleY)) {
            throw new IllegalArgumentException("Grid coordinates must be finite");
        }
    }

    public int floorX() {
        return (int) Math.floor(sampleX);
    }

    public int floorY() {
        return (int) Math.floor(sampleY);
    }
}
