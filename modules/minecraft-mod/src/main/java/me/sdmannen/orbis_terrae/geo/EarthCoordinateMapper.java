package me.sdmannen.orbis_terrae.geo;

import java.util.Objects;
import me.sdmannen.orbis_terrae.atlas.api.GeoCoordinate;
import me.sdmannen.orbis_terrae.profile.WorldProfile;

/** Converts centered Minecraft block coordinates to an equirectangular Earth projection. */
public final class EarthCoordinateMapper {
    private final int projectedWidthBlocks;
    private final int projectedHeightBlocks;
    private final int minimumX;
    private final int minimumZ;

    public EarthCoordinateMapper(WorldProfile profile) {
        Objects.requireNonNull(profile, "profile");
        projectedWidthBlocks = profile.horizontalScale().projectedWidthBlocks();
        projectedHeightBlocks = profile.horizontalScale().projectedHeightBlocks();
        minimumX = -projectedWidthBlocks / 2;
        minimumZ = -projectedHeightBlocks / 2;
    }

    public GeoCoordinate toGeographic(long blockX, long blockZ) {
        long wrappedX = wrapBlockX(blockX);
        double longitude = normalizeLongitude((double) wrappedX / projectedWidthBlocks * 360.0);
        double latitude = clampLatitude(-(double) blockZ / projectedHeightBlocks * 180.0);
        return new GeoCoordinate(latitude, longitude);
    }

    public BlockCoordinate toBlock(GeoCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        double longitude = normalizeLongitude(coordinate.longitude());
        double blockX = longitude / 360.0 * projectedWidthBlocks;
        double blockZ = -coordinate.latitude() / 180.0 * projectedHeightBlocks;
        return new BlockCoordinate(blockX, blockZ);
    }

    public long wrapBlockX(long blockX) {
        return Math.floorMod(blockX - minimumX, projectedWidthBlocks) + minimumX;
    }

    public boolean containsProjectedLatitude(long blockZ) {
        return blockZ >= minimumZ && blockZ < maximumZExclusive();
    }

    public int minimumX() {
        return minimumX;
    }

    public int maximumXExclusive() {
        return minimumX + projectedWidthBlocks;
    }

    public int minimumZ() {
        return minimumZ;
    }

    public int maximumZExclusive() {
        return minimumZ + projectedHeightBlocks;
    }

    public int projectedWidthBlocks() {
        return projectedWidthBlocks;
    }

    public int projectedHeightBlocks() {
        return projectedHeightBlocks;
    }

    private static double normalizeLongitude(double longitude) {
        double normalized = longitude - 360.0 * Math.floor((longitude + 180.0) / 360.0);
        return normalized == 180.0 ? -180.0 : normalized;
    }

    private static double clampLatitude(double latitude) {
        double clamped = Math.max(-90.0, Math.min(90.0, latitude));
        return clamped == 0.0 ? 0.0 : clamped;
    }

    /** Projected Minecraft block coordinate before integer chunk rounding. */
    public record BlockCoordinate(double x, double z) {
        public BlockCoordinate {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Block coordinates must be finite");
            }
        }
    }
}
