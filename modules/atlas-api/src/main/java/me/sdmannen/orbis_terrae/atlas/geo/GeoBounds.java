package me.sdmannen.orbis_terrae.atlas.geo;

public record GeoBounds(double west, double south, double east, double north) {
    public GeoBounds {
        if (!Double.isFinite(west) || !Double.isFinite(south)
                || !Double.isFinite(east) || !Double.isFinite(north)) {
            throw new IllegalArgumentException("Bounds must be finite");
        }
        if (west >= east || south >= north) {
            throw new IllegalArgumentException("Bounds must have positive width and height");
        }
        if (west < -180.0 || east > 180.0 || south < -90.0 || north > 90.0) {
            throw new IllegalArgumentException("Bounds exceed latitude/longitude limits");
        }
    }

    public boolean contains(double latitude, double longitude) {
        return longitude >= west && longitude <= east
                && latitude >= south && latitude <= north;
    }
}
