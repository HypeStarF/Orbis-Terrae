package me.sdmannen.orbis_terrae.atlas.api;

/** Geographic latitude and longitude in decimal degrees. */
public record GeoCoordinate(double latitude, double longitude) {
    public GeoCoordinate {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be finite and within [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be finite and within [-180, 180]");
        }
    }
}
